/* ============================================================
   Слой API личного кабинета администратора API.

   Авторизация — cookie сессии (OAuth2, паттерн BFF), поэтому во
   всех запросах credentials: 'include'. Заголовки Telegram здесь
   не нужны: это обычный сайт, а не Mini App.

   Эндпоинты (подтверждены коллекцией Postman):
     GET   /api/private/client?id&username&from&to&page&size
           -> { content: [...], page: {size, number, totalElements, totalPages} }
           статуса у клиента больше нет; methods — массив кодов RequestMethod
     PATCH /api/private/client/{id}   <- { orderTimeoutSeconds?, commissionPercent?, methods? }
           обновляются только поля, не равные null;
           methods: [] отвязывает все способы оплаты
     GET   /api/private/dictionary    -> { RequestMethod: [{name, description}], ... }
     GET   /api/private/merchant-config/{clientId}
     PATCH /api/private/merchant-config/{id}
     GET   /api/private/withdrawal-request?client&id&status&address&createdAtFrom&createdAtTo&page&size&sort
     PATCH /api/private/withdrawal-request/{id}?status=APPROVED|CANCELED
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

  // 401 — сессия истекла (сервер может ответить и редиректом на форму
  // входа — тогда вместо JSON придёт HTML, это ловится ниже).
  // 403 — вход есть, но прав на действие нет.
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

  // Ошибки бэк отдаёт как application/problem+json — это тоже JSON,
  // иначе текст из description до пользователя не дойдёт.
  const ct = res.headers.get('content-type') || '';
  const isJson = ct.includes('json');
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

  // Справочники (ClientStatus, OrderStatus и другие перечисления).
  dictionary: () => request(`${API}/dictionary`),

  /* Ордера. Только чтение: список с фильтром и одна запись.
     Возвращает { items, total, totalPages } — как и клиенты. */
  async orders(params) {
    const d = await request(`${API}/order`, { params });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  order: (id) => request(`${API}/order/${encodeURIComponent(id)}`),

  merchantConfigs: (clientId) =>
      request(`${API}/merchant-config/${encodeURIComponent(clientId)}`),
  updateMerchantConfig: (id, body) =>
      request(`${API}/merchant-config/${encodeURIComponent(id)}`, { method: 'PATCH', body }),

  /* Заявки на вывод всех клиентов. Возвращает { items, total, totalPages }.
     Сортировку передаём явно: по ТЗ новые сверху. */
  async withdrawals(params) {
    const d = await request(`${API}/withdrawal-request`, {
      params: { sort: 'createdAt,desc', ...params },
    });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  /* Подтверждение (APPROVED) или отмена (CANCELED) заявки.
     Статус уходит параметром в адресе, тела нет. Если заявка уже
     не в статусе NEW, бэк отвечает 400. */
  setWithdrawalStatus: (id, status) =>
      request(`${API}/withdrawal-request/${encodeURIComponent(id)}`, {
        method: 'PATCH',
        params: { status },
      }),
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

/* Дата из поля <input type="date"> (строка ГГГГ-ММ-ДД) -> миллисекунды.
   Бэк понимает промежуток как [от, до): «от» включительно, «до» — нет.
   edge: 'start' — начало суток; 'end' — начало СЛЕДУЮЩИХ суток,
   чтобы весь день попал в выборку целиком. */
export function dateToMs(value, edge = 'start') {
  if (!value) return '';
  const [y, m, d] = value.split('-').map(Number);
  if (!y || !m || !d) return '';
  const date = edge === 'end'
      ? new Date(y, m - 1, d + 1, 0, 0, 0, 0)
      : new Date(y, m - 1, d, 0, 0, 0, 0);
  return date.getTime();
}

// Комиссия: дробное число, может быть null.
export const fmtPercent = (v) =>
    v == null || v === '' ? '—' : `${Number(v).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} %`;

export const fmtSeconds = (v) =>
    v == null || v === '' ? '—' : `${Number(v).toLocaleString('ru-RU')} сек`;

/* Баланс клиента: рубли, разделитель разрядов, две цифры после запятой.
   ВНИМАНИЕ: имя поля в ответе бэка не подтверждено — в коллекции Postman
   баланса нет. Сейчас читаем client.balance, см. ClientCard в App.jsx. */
export function fmtMoney(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (!Number.isFinite(n)) return '—';
  return `${n.toLocaleString('ru-RU', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} ₽`;
}

/* Дата и необязательное время -> миллисекунды.
   date — строка ГГГГ-ММ-ДД из <input type="date">,
   time — ЧЧ:ММ:СС (или ЧЧ:ММ) из <input type="time">.
   Бэк понимает промежуток как [от, до): «от» включительно, «до» — нет.
   Без времени: 'start' — начало суток, 'end' — начало следующих суток.
   Со временем: 'start' — начало этой секунды, 'end' — начало следующей,
   чтобы секунда попала в выборку целиком (у createdAt есть миллисекунды). */
export function dateTimeToMs(date, time, edge = 'start') {
  if (!date) return '';
  if (!time) return dateToMs(date, edge);
  const [y, m, d] = date.split('-').map(Number);
  const [hh = 0, mm = 0, ss = 0] = time.split(':').map(Number);
  if (!y || !m || !d) return '';
  const ms = new Date(y, m - 1, d, hh, mm, ss, 0).getTime();
  if (Number.isNaN(ms)) return '';
  return edge === 'end' ? ms + 1000 : ms;
}

/* Сокращённое значение: начало, многоточие, конец — b6e2f1a0…1b02. */
export function shortValue(value, head = 8, tail = 4) {
  const s = String(value ?? '');
  if (!s) return '—';
  return s.length > head + tail + 1 ? `${s.slice(0, head)}…${s.slice(-tail)}` : s;
}

/* Курс ₽/USDT: всегда два знака после запятой. */
export function fmtRate(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  return Number.isFinite(n)
      ? n.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '—';
}