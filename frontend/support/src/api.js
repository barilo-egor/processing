/* ============================================================
   Слой API личного кабинета администратора API.

   Авторизация — cookie сессии (OAuth2, паттерн BFF), поэтому во
   всех запросах credentials: 'include'. Заголовки Telegram здесь
   не нужны: это обычный сайт, а не Mini App.

   Эндпоинты (подтверждены коллекцией Postman):
     GET   /api/support/client?id&username&from&to&page&size
           -> { content: [...], page: {size, number, totalElements, totalPages} }
           статуса у клиента больше нет; methods — массив кодов RequestMethod
     PATCH /api/support/client/{id}   <- { orderTimeoutSeconds?, commissionPercent?, methods? }
           обновляются только поля, не равные null;
           methods: [] отвязывает все способы оплаты
     GET   /api/common/dictionary     -> { RequestMethod: [{name, description}], ... }
     GET   /api/support/merchant-config/{clientId}
     PATCH /api/support/merchant-config/{id}
     GET   /api/support/withdrawal-request?client&id&status&address&createdAtFrom&createdAtTo&page&size&sort
     PATCH /api/support/withdrawal-request/{id}?status=APPROVED|CANCELED
     GET   /api/support/order/{id}   -> подробности ордера
     PATCH /api/support/order/{id}   <- { status: SUCCESS | CANCELED | DISPUTE } — ручная смена статуса
     GET   /api/support/transaction?id&client&createdAtFrom&createdAtTo&page&size&sort
     POST  /api/support/transaction   <- { clientId, operation, amount, comment }
           тип MANUAL_CORRECT бэк ставит сам
     GET   /api/support/docs           -> [{ id, title }] — страницы документации для роли
     GET   /api/support/docs/{id}      -> { id, title, content } — content в Markdown; нет — 404
     GET   /api/support/support-user  -> { id, username, registeredAt } — вошедший администратор
     GET   /api/support/support-user/roles -> ["ADMIN"] | ["OPERATOR"] — роли вошедшего пользователя
     GET   /api/support/merchant-callback?orderId&merchant&merchantOrderId&createdAtFrom&createdAtTo&page&size&sort
           -> { content: [{ id, createdAt, processedAt, orderId, merchant, merchantOrderId, status, statusDescription }], page }
     GET   /api/support/incident?id&status&type&createdAtFrom&createdAtTo&resolvedAtFrom&resolvedAtTo&page&size&sort
           -> { content: [{ id, createdAt, status, errorTimestamp, type, data, resolvedById, resolvedAt, comment }], page }
     PATCH /api/support/incident/{id}  <- { comment } — обработка: NEW → RESOLVED
     POST  /api/support/client/{id}/balance/recalculation -> клиент после пересчёта баланса
     GET   /api/support/merchant-history?operationId&orderId&actorId&details&merchants&amount&createdAtFrom&createdAtTo
           -> { content, page }; orderId здесь — ID у мерчанта
   ============================================================ */

// API поддержки ({{processing_support_api_url}} в коллекции).
const SUPPORT_API = '/api/support';

// Общее API для всех вошедших пользователей: словарь.
const COMMON_API = '/api/common';

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

/* Выход: POST /logout обычной формой, а не fetch — так браузер сам проходит
   редирект бэка на выход из Keycloak (OIDC logout) и закрывается сессия и там.
   CSRF: бэк настроен через .spa() — в поле формы _csrf он принимает только
   замаскированный токен, а не значение куки XSRF-TOKEN. Замаскированный
   токен берём со стандартной страницы подтверждения Spring (GET /logout):
   в её форме есть <input name="_csrf">. Страницу пользователю не показываем.
   Если токен не нашёлся (страницу отключили или поменяли) — просто переходим
   на /logout: там будет та же страница подтверждения, выход всё равно сработает. */
export async function logout() {
  let token = '';
  try {
    const res = await fetch('/logout', { credentials: 'include', headers: { Accept: 'text/html' } });
    if (res.ok) {
      const html = await res.text();
      const doc = new DOMParser().parseFromString(html, 'text/html');
      token = doc.querySelector('input[name="_csrf"]')?.value || '';
    }
  } catch { /* сеть недоступна — ниже запасной вариант */ }

  if (!token) {
    window.location.href = '/logout';
    return;
  }

  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  form.style.display = 'none';
  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = '_csrf';
  input.value = token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}

// Перезагрузка из-за 401 уже запущена — повторно не запускаем.
let reloadingOn401 = false;

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
  // 401 — сессия истекла: перезагружаем страницу, сервер сам отправит на вход
  // (Keycloak) и после входа вернёт сюда же. Промис не завершаем, чтобы до
  // перезагрузки не мелькали тосты с ошибкой; при нескольких 401 сразу
  // перезагрузка запускается один раз.
  if (res.status === 401) {
    if (!reloadingOn401) {
      reloadingOn401 = true;
      window.location.reload();
    }
    return new Promise(() => {});
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
    if (Array.isArray(v)) {
      // Список — повтором параметра (merchants=A&merchants=B), так его понимает Spring.
      v.forEach((x) => { if (x !== undefined && x !== null && x !== '') q.append(k, x); });
    } else if (v !== undefined && v !== null && v !== '') {
      q.append(k, v);
    }
  });
  const s = q.toString();
  if (!s) return url;
  return `${url}${url.includes('?') ? '&' : '?'}${s}`;
}

export const api = {
  // Список клиентов. Возвращает { items, total, totalPages }.
  async clients(params) {
    const d = await request(`${SUPPORT_API}/client`, { params });
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
      request(`${SUPPORT_API}/client/${encodeURIComponent(id)}`, { method: 'PATCH', body }),

  // Справочники (ClientStatus, OrderStatus и другие перечисления).
  dictionary: () => request(`${COMMON_API}/dictionary`),

  /* Ордера. Только чтение: список с фильтром и одна запись.
     Возвращает { items, total, totalPages } — как и клиенты. */
  async orders(params) {
    const d = await request(`${SUPPORT_API}/order`, { params });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  order: (id) => request(`${SUPPORT_API}/order/${encodeURIComponent(id)}`),

  /* Ручная смена статуса ордера: SUCCESS, CANCELED или DISPUTE.
     Переход проверяет бэк; запрещённый или уже выполненный — 400 с текстом. */
  setOrderStatus: (id, status) =>
      request(`${SUPPORT_API}/order/${encodeURIComponent(id)}`, { method: 'PATCH', body: { status } }),

  merchantConfigs: (clientId) =>
      request(`${SUPPORT_API}/merchant-config/${encodeURIComponent(clientId)}`),
  updateMerchantConfig: (id, body) =>
      request(`${SUPPORT_API}/merchant-config/${encodeURIComponent(id)}`, { method: 'PATCH', body }),

  /* Заявки на вывод всех клиентов. Возвращает { items, total, totalPages }.
     Сортировка по умолчанию — новые сверху (по ТЗ); params.sort её заменяет. */
  async withdrawals(params) {
    const d = await request(`${SUPPORT_API}/withdrawal-request`, {
      params: { sort: 'createdAt,desc', ...params },
    });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  /* Транзакции всех клиентов (сервис billing). Возвращает { items, total, totalPages }.
     Сортировку передаём явно: по ТЗ новые сверху. */
  async transactions(params) {
    const d = await request(`${SUPPORT_API}/transaction`, {
      params: { sort: 'createdAt,desc', ...params },
    });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  // Документация: список страниц для роли пользователя [{ id, title }] — порядок = порядок в меню.
  async docs() {
    const d = await request(`${SUPPORT_API}/docs`);
    return Array.isArray(d) ? d.filter((x) => x && x.id && x.title) : [];
  },

  // Страница документации: { id, title, content }, content — Markdown (GFM). Нет страницы — 404.
  doc: (id) => request(`${SUPPORT_API}/docs/${encodeURIComponent(id)}`),

  // Вошедший администратор (для шапки): { id, username, registeredAt }.
  supportUser: () => request(`${SUPPORT_API}/support-user`),

  // Роли вошедшего пользователя поддержки: массив строк, например ["ADMIN"].
  async supportUserRoles() {
    const d = await request(`${SUPPORT_API}/support-user/roles`);
    return Array.isArray(d) ? d.map(String) : [];
  },

  /* Журнал коллбэков от мерчантов. Возвращает { items, total, totalPages }.
     Сортировка по умолчанию — новые сверху (по ТЗ); params.sort её заменяет. */
  async merchantCallbacks(params) {
    const d = await request(`${SUPPORT_API}/merchant-callback`, {
      params: { sort: 'createdAt,desc', ...params },
    });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  /* История мерчантов: выдачи реквизитов по запросам клиентов API
     (сервис merchant_history, только записи processing). Возвращает { items, total, totalPages }.
     Параметры (коллекция processing): operationId — ID ордера, orderId — ID у мерчанта (!),
     actorId — ID клиента, details, merchants[], amount, createdAtFrom/To (мс), page, size, sort. */
  async merchantHistory(params) {
    const d = await request(`${SUPPORT_API}/merchant-history`, { params });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  /* Инциденты системы. Возвращает { items, total, totalPages }.
     Сортировка по умолчанию — новые сверху (по ТЗ); params.sort её заменяет. */
  async incidents(params) {
    const d = await request(`${SUPPORT_API}/incident`, {
      params: { sort: 'createdAt,desc', ...params },
    });
    const items = Array.isArray(d?.content) ? d.content : [];
    return {
      items,
      total: d?.page?.totalElements ?? items.length,
      totalPages: d?.page?.totalPages ?? 1,
    };
  },

  /* Обработка инцидента: NEW → RESOLVED. Комментарий необязателен —
     пустую строку бэк сохраняет как null. Обработавшим бэк ставит текущего пользователя. */
  resolveIncident: (id, comment) =>
      request(`${SUPPORT_API}/incident/${encodeURIComponent(id)}`, { method: 'PATCH', body: { comment } }),

  // Перерасчёт баланса клиента по сумме его транзакций. Возвращает клиента после пересчёта.
  recalculateBalance: (clientId) =>
      request(`${SUPPORT_API}/client/${encodeURIComponent(clientId)}/balance/recalculation`, { method: 'POST' }),

  /* Ручная корректировка баланса: { clientId, operation, amount, comment }.
     Тип «Ручная корректировка» (MANUAL_CORRECT) бэк проставляет сам. */
  createTransaction: (body) =>
      request(`${SUPPORT_API}/transaction`, { method: 'POST', body }),

  /* Подтверждение (APPROVED) или отмена (CANCELED) заявки.
     Статус уходит параметром в адресе, тела нет. Если заявка уже
     не в статусе NEW, бэк отвечает 400. */
  setWithdrawalStatus: (id, status) =>
      request(`${SUPPORT_API}/withdrawal-request/${encodeURIComponent(id)}`, {
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