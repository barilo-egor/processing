/* ============================================================
   Слой API личного кабинета клиента API.

   Авторизация — cookie сессии (OAuth2, паттерн BFF), поэтому во
   всех запросах credentials: 'include'.

   Эндпоинты (из коллекции processing.postman_collection):
     GET    /api/private/api-key            -> [{id, name, preview}]
     POST   /api/private/api-key?name=      -> строка с полным токеном
     DELETE /api/private/api-key/{id}
     PATCH  /api/private/client/{id}        <- {callbackUrl}
     GET    /api/private/client             -> {content: [...], page: {...}}

   ВНИМАНИЕ, требует подтверждения у бэкенда:
   отдельного эндпоинта «свой профиль» в коллекции нет. Считаем, что
   для роли ROLE_CLIENT запрос GET /api/private/client возвращает
   только собственную запись клиента — из неё берём id и callbackUrl.
   Если появится /api/private/client/me — заменить в loadProfile()
   одну строку.
   ============================================================ */

const API = '/api/private';

/* Защита от подделки запросов (CSRF).
   Сервер кладёт секрет в куку XSRF-TOKEN и требует то же значение
   в заголовке X-XSRF-TOKEN при каждом изменяющем запросе. */
function xsrfToken() {
  try {
    const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
  } catch { return ''; }
}

/* Единая точка входа для запросов.
   options:
     method  — 'GET' по умолчанию
     body    — объект (сериализуется) или готовая строка
     params  — объект query-параметров, пустые отбрасываются
     raw     — true, если ответ приходит строкой, а не JSON
               (так отдаётся созданный токен) */
export async function request(url, options = {}) {
  const { params, body, headers, raw, ...rest } = options;

  const method = (rest.method || 'GET').toUpperCase();
  const needsCsrf = method !== 'GET' && method !== 'HEAD';
  const token = needsCsrf ? xsrfToken() : '';

  let res;
  try {
    res = await fetch(buildUrl(url, params), {
      ...rest,
      credentials: 'include', // cookie сессии
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': token } : {}),
        ...(headers || {}),
      },
      ...(body !== undefined
          ? { body: typeof body === 'string' ? body : JSON.stringify(body) }
          : {}),
    });
  } catch {
    throw new Error('Нет связи с сервером');
  }

  if (res.status === 401 || res.status === 403) {
    const err = new Error('Сессия истекла, войдите заново');
    err.unauthorized = true;
    throw err;
  }

  const ct = res.headers.get('content-type') || '';
  const isJson = ct.includes('application/json') || ct.includes('problem+json');
  const text = await res.text().catch(() => '');

  // Пришла HTML-страница вместо данных — почти наверняка форма входа
  // после редиректа. Считаем это истёкшей сессией.
  if (!isJson && text.trimStart().startsWith('<')) {
    const err = new Error('Сессия истекла, войдите заново');
    err.unauthorized = true;
    throw err;
  }

  let data = text;
  if (isJson || !raw) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  // Бэк иногда отдаёт тело ошибки при формально успешном коде,
  // поэтому проверяем и содержимое: {status, title, description}.
  const errorish = data && typeof data === 'object' && !Array.isArray(data)
      && typeof data.status === 'number' && data.status >= 400;

  if (!res.ok || errorish) {
    const msg = (data && typeof data === 'object'
            && (data.description || data.title || data.message || data.error))
        || `Ошибка ${res.status}`;
    const err = new Error(msg);
    err.status = errorish ? data.status : res.status;
    throw err;
  }

  return data;
}

function buildUrl(url, params) {
  if (!params) return url;
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.append(k, v);
  });
  const s = q.toString();
  if (!s) return url;
  return `${url}${url.includes('?') ? '&' : '?'}${s}`;
}

export const api = {
  /* Профиль текущего клиента.
     См. примечание в шапке файла: берём первую (и единственную для
     роли клиента) запись из списка клиентов. */
  async profile() {
    const d = await request(`${API}/client`);
    const items = Array.isArray(d?.content) ? d.content : [];
    return items[0] || null;
  },

  // Сохранение Callback URL. Шлём только это поле:
  // бэк обновляет всё, что не null.
  saveCallbackUrl: (id, callbackUrl) =>
      request(`${API}/client/${encodeURIComponent(id)}`, {
        method: 'PATCH',
        body: { callbackUrl },
      }),

  // Список токенов: [{id, name, preview}].
  async apiKeys() {
    const d = await request(`${API}/api-key`);
    return Array.isArray(d) ? d : [];
  },

  // Создание токена. Ответ — строка с полным значением,
  // показывается один раз и больше не доступна.
  createApiKey: (name) =>
      request(`${API}/api-key`, { method: 'POST', params: { name }, raw: true }),

  deleteApiKey: (id) =>
      request(`${API}/api-key/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};

/* ---------------- Формат и проверки ---------------- */

/* Проверка Callback URL. Пустое значение допустимо — по ТЗ это
   означает, что уведомления не отправляются. */
export function isValidCallbackUrl(value) {
  const v = String(value || '').trim();
  if (!v) return true;
  let u;
  try { u = new URL(v); } catch { return false; }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
  return Boolean(u.hostname);
}