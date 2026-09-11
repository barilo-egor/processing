/* ============================================================
   Слой API личного кабинета администратора API.

   Авторизация — cookie сессии (OAuth2, паттерн BFF), поэтому во
   всех запросах credentials: 'include'. Заголовки Telegram здесь
   не нужны: это обычный сайт, а не Mini App.

   Эндпоинты (подтверждены коллекцией Postman):
     GET   /api/private/client?id&username&status&from&to&page&size
           -> { content: [...], page: {size, number, totalElements, totalPages} }
     PATCH /api/private/client/{id}   <- { status?, orderTimeoutSeconds?, commissionPercent?, callbackUrl? }
           обновляются только поля, не равные null
     GET   /api/private/dictionary    -> { ClientStatus: [{name, description}] }
     GET   /api/private/merchant-config/{clientId}
     PATCH /api/private/merchant-config/{id}
   ============================================================ */

const API = '/api/private';

/* Защита от подделки запросов (CSRF).
   Сервер кладёт секрет в куку XSRF-TOKEN и требует то же значение
   в заголовке X-XSRF-TOKEN при каждом изменяющем запросе.
   Чужой сайт куку прочитать не может, поэтому подделать заголовок не сумеет.
   Без этого заголовка PATCH/POST/DELETE отклоняются. */
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
     params  — объект query-параметров, пустые отбрасываются */
export async function request(url, options = {}) {
  const { params, body, headers, ...rest } = options;

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

  // Сессия истекла. Сервер при этом может ответить редиректом на форму
  // входа — тогда вместо JSON придёт HTML, это ловится ниже.
  if (res.status === 401 || res.status === 403) {
    const err = new Error('Сессия истекла, войдите заново');
    err.unauthorized = true;
    throw err;
  }

  const ct = res.headers.get('content-type') || '';
  const isJson = ct.includes('application/json');
  const data = isJson
    ? await res.json().catch(() => null)
    : await res.text().catch(() => null);

  // Пришла HTML-страница вместо данных — почти наверняка форма входа
  // после редиректа. Считаем это истёкшей сессией.
  if (!isJson && typeof data === 'string' && data.trimStart().startsWith('<')) {
    const err = new Error('Сессия истекла, войдите заново');
    err.unauthorized = true;
    throw err;
  }

  if (!res.ok) {
    // Формат ошибки бэка: { status, title, description, ... }
    const msg = (data && typeof data === 'object'
      && (data.description || data.title || data.message || data.error))
      || `Ошибка ${res.status}`;
    const err = new Error(msg);
    err.status = res.status;
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
  // Список клиентов. Возвращает { items, total, totalPages }.
  async clients(params) {
    const d = await request(`${API}/client`, { params });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  // Обновление клиента. Шлём ТОЛЬКО изменённое поле:
  // бэк обновляет всё, что не null, поэтому лишние поля перезапишут данные.
  updateClient: (id, body) =>
    request(`${API}/client/${encodeURIComponent(id)}`, { method: 'PATCH', body }),

  // Справочники (ClientStatus и другие перечисления).
  dictionary: () => request(`${API}/dictionary`),

  merchantConfigs: (clientId) =>
    request(`${API}/merchant-config/${encodeURIComponent(clientId)}`),
  updateMerchantConfig: (id, body) =>
    request(`${API}/merchant-config/${encodeURIComponent(id)}`, { method: 'PATCH', body }),
};

/* ---------------- Формат и проверки ---------------- */

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isUuid = (s) => UUID_RE.test(String(s || '').trim());

// registeredAt приходит как UNIX-время в миллисекундах.
export function fmtDateTime(ms) {
  if (ms == null || ms === '') return '—';
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

// Дата из поля <input type="date"> (строка ГГГГ-ММ-ДД) -> миллисекунды.
// edge: 'start' — начало суток, 'end' — конец суток.
export function dateToMs(value, edge = 'start') {
  if (!value) return '';
  const [y, m, d] = value.split('-').map(Number);
  if (!y || !m || !d) return '';
  const date = edge === 'end'
    ? new Date(y, m - 1, d, 23, 59, 59, 999)
    : new Date(y, m - 1, d, 0, 0, 0, 0);
  return date.getTime();
}

// Комиссия: дробное число, может быть null.
export const fmtPercent = (v) =>
  v == null || v === '' ? '—' : `${Number(v).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} %`;

export const fmtSeconds = (v) =>
  v == null || v === '' ? '—' : `${Number(v).toLocaleString('ru-RU')} сек`;
