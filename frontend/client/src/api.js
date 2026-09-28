/* ============================================================
   Слой API личного кабинета клиента API.

   Авторизация — cookie сессии (OAuth2, паттерн BFF), поэтому во
   всех запросах credentials: 'include'.

   Эндпоинты (из коллекции processing.postman_collection).
   Всё клиентское API — под /api/v1, словарь — общий, в /api/private:
     GET    /api/v1/client                  -> {id, username, callbackUrl, ...}
     PATCH  /api/v1/client                  <- {callbackUrl}  (клиент меняет только его)
     GET    /api/v1/api-key                 -> [{id, name, preview}]
     POST   /api/v1/api-key?name=           -> строка с полным токеном
            (в коллекции пока нет — путь по аналогии, ждём от бэка)
     DELETE /api/v1/api-key/{id}
     GET    /api/v1/order?status&method&page&size&sort
     GET    /api/v1/transaction?createdAtFrom&createdAtTo&page&size&sort
     GET    /api/v1/withdrawal-request?id&status&address&createdAtFrom&createdAtTo&page&size&sort
     POST   /api/v1/withdrawal-request?amount=&address=   — создание
     PATCH  /api/v1/withdrawal-request/{id}               — отмена
     GET    /api/private/dictionary         -> {ИмяПеречисления: [...]}

   Ошибки 400 бэк отдаёт с понятным текстом в description — его и
   показываем пользователю.
   ============================================================ */

const API = '/api/private';

// Клиентское API ({{processing_v1_api_url}} в коллекции).
const API_V1 = '/api/v1';

// Все списки разделов: по 25 записей, новые сверху.
export const PAGE_SIZE = 25;
const SORT_NEWEST = 'createdAt,desc';

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

  // 401 — сессия истекла, 403 — вход есть, но прав на действие нет.
  if (res.status === 401) {
    const err = new Error('Сессия истекла, войдите заново');
    err.unauthorized = true;
    throw err;
  }
  if (res.status === 403) {
    const err = new Error('Нет доступа');
    err.unauthorized = true;
    err.status = 403;
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
  // Профиль текущего клиента: один объект, клиент определяется на бэке.
  profile: () => request(`${API_V1}/client`),

  // Сохранение Callback URL. Клиент сам себе меняет только это поле;
  // id не нужен — бэк берёт его из сессии.
  saveCallbackUrl: (callbackUrl) =>
      request(`${API_V1}/client`, {
        method: 'PATCH',
        body: { callbackUrl },
      }),

  // Список токенов: [{id, name, preview}].
  async apiKeys() {
    const d = await request(`${API_V1}/api-key`);
    return Array.isArray(d) ? d : [];
  },

  // Создание токена. Ответ — строка с полным значением,
  // показывается один раз и больше не доступна.
  createApiKey: (name) =>
      request(`${API_V1}/api-key`, { method: 'POST', params: { name }, raw: true }),

  deleteApiKey: (id) =>
      request(`${API_V1}/api-key/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  // Словари перечислений: статусы, способы оплаты, операции и т.д.
  async dictionary() {
    const d = await request(`${API}/dictionary`);
    return d && typeof d === 'object' ? d : {};
  },

  // Ордера клиента. Фильтр: status, method.
  orders: (params) => pageRequest(`${API_V1}/order`, params),

  // Транзакции. Фильтр: createdAtFrom, createdAtTo.
  transactions: (params) => pageRequest(`${API_V1}/transaction`, params),

  // Заявки на вывод. Фильтр: id и address (подстрока), status,
  // createdAtFrom, createdAtTo.
  withdrawals: (params) => pageRequest(`${API_V1}/withdrawal-request`, params),

  // Создание заявки: сумма в рублях и адрес — query-параметрами.
  createWithdrawal: (amount, address) =>
      request(`${API_V1}/withdrawal-request`, {
        method: 'POST',
        params: { amount, address },
      }),

  // Отмена заявки. Возможна только в статусе NEW, иначе бэк отвечает 400.
  cancelWithdrawal: (id) =>
      request(`${API_V1}/withdrawal-request/${encodeURIComponent(id)}`, { method: 'PATCH' }),
};

/* Запрос страницы списка. Размер и сортировка общие для всех
   разделов. Ответ приводим к виду {rows, total}. */
async function pageRequest(url, params = {}) {
  const d = await request(url, {
    params: { size: PAGE_SIZE, sort: SORT_NEWEST, ...params },
  });
  const rows = Array.isArray(d?.content) ? d.content : Array.isArray(d) ? d : [];
  const total = Number(d?.page?.totalElements ?? d?.totalElements ?? rows.length) || 0;
  return { rows, total };
}

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

/* Адрес USDT-кошелька. По ТЗ проверяется только, что он заполнен;
   формат сети не проверяем. */
export function isValidWalletAddress(value) {
  return String(value || '').trim() !== '';
}

/* Сумма: целое число больше нуля, без знаков и пробелов. */
export function isPositiveInt(value) {
  const v = String(value || '').trim();
  return /^\d+$/.test(v) && Number(v) > 0;
}

/* Сокращение длинных значений: «9f1c2a84…2d41». */
export function shorten(value, head = 8, tail = 4) {
  const s = String(value ?? '');
  if (s.length <= head + tail + 1) return s;
  return `${s.slice(0, head)}…${s.slice(-tail)}`;
}

/* Число по-русски: «25 000», «2,5». */
export function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '-';
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return n.toLocaleString('ru-RU', { maximumFractionDigits: digits });
}

/* Курс — всегда два знака: «92,45». */
export function formatRate(value) {
  if (value === null || value === undefined || value === '') return '-';
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return n.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/* Дата и время «19.09.2026 14:22:10». Понимает unix ms, ISO-строку
   и уже готовую строку в этом формате. */
export function formatDateTime(value) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'string' && /^\d{2}\.\d{2}\.\d{4}/.test(value)) return value;
  const d = new Date(typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value);
  if (Number.isNaN(d.getTime())) return String(value);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} `
      + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/* ---------------- Словари ---------------- */

/* Элементы перечисления из словаря в виде [{value, label}].
   Показываем displayName (или description), шлём name — как в
   фильтре мерчантов кабинета администратора. */
export function dictItems(dict, key) {
  const src = dict?.[key];
  if (Array.isArray(src)) {
    return src.map((x) => (typeof x === 'string'
        ? { value: x, label: x }
        : {
          value: x?.name ?? x?.value ?? x?.code,
          label: x?.displayName ?? x?.description ?? x?.title ?? x?.name ?? x?.value,
        }));
  }
  if (src && typeof src === 'object') {
    return Object.entries(src).map(([k, v]) => ({
      value: k,
      label: typeof v === 'string' ? v : (v?.displayName ?? v?.description ?? k),
    }));
  }
  return [];
}

/* Подпись значения перечисления; если в словаре нет — само значение. */
export function dictLabel(dict, key, value) {
  if (value === null || value === undefined || value === '') return '-';
  const hit = dictItems(dict, key).find((x) => x.value === value);
  return hit?.label || String(value);
}

/* ---------------- Фильтр по дате ---------------- */

export const EMPTY_DATE_FILTER = {
  mode: 'eq',          // 'eq' — «Равна», 'range' — «Диапазон»
  date: '', time: '',
  fromDate: '', fromTime: '',
  toDate: '', toTime: '',
};

/* Момент времени в unix ms по местному времени.
   Бэк понимает промежуток как [от, до): «от» включительно, «до» — нет.
   Поэтому верхняя граница — начало следующей секунды (если время
   указано) или начало следующих суток (если нет): так последняя
   секунда или весь день попадают в выборку целиком. */
function toMs(date, time, end = false) {
  const [y, m, d] = String(date).split('-').map(Number);
  if (!y || !m || !d) return undefined;
  if (!time) {
    return new Date(y, m - 1, end ? d + 1 : d, 0, 0, 0, 0).getTime();
  }
  const [hh = 0, mm = 0, ss = 0] = time.split(':').map(Number);
  const t = new Date(y, m - 1, d, hh, mm, ss, 0).getTime();
  if (Number.isNaN(t)) return undefined;
  return end ? t + 1000 : t;
}

/* Параметры createdAtFrom / createdAtTo — unix epoch millis, как в
   фильтре клиентов кабинета администратора.
   Время необязательно: без него поиск идёт по целым суткам.
   «Равна» без времени — весь день, со временем — ровно эта секунда.
   (В «Кейсах» формат другой — строка, там свой сервис.) */
export function dateFilterParams(f) {
  if (!f) return {};
  if (f.mode === 'eq') {
    if (!f.date) return {};
    return {
      createdAtFrom: toMs(f.date, f.time),
      createdAtTo: toMs(f.date, f.time, true),
    };
  }
  return {
    createdAtFrom: f.fromDate ? toMs(f.fromDate, f.fromTime) : undefined,
    createdAtTo: f.toDate ? toMs(f.toDate, f.toTime, true) : undefined,
  };
}