/* ============================================================
   Слой API личного кабинета администратора API.

   ВНИМАНИЕ: адреса эндпоинтов пока не подтверждены бэкендом —
   константы ниже нужно заполнить, когда придут контракты.
   Ждём: адреса и методы списков клиентов/ордеров, где передавать
   page/size и фильтры (query или тело), как приходит общее
   количество (X-Total-Count или поле в ответе), адрес изменения
   статуса и таймаута клиента.

   Авторизация — cookie сессии, поэтому во всех запросах
   credentials: 'include'. Заголовок X-TG-Init-Data здесь НЕ нужен:
   это не Telegram Mini App, а обычный сайт.
   ============================================================ */

const ENDPOINTS = {
  clients: '',        // TODO: список клиентов
  client: '',         // TODO: изменение клиента (статус, таймаут)
  orders: '',         // TODO: список ордеров
};

/* Единая точка входа для запросов.
   options:
     method   — 'GET' по умолчанию
     body     — объект (сериализуется) или готовая строка
     params   — объект query-параметров, пустые отбрасываются
     withTotal — вернуть {items, total} из X-Total-Count вместо массива */
export async function request(url, options = {}) {
  const { params, body, headers, withTotal = false, ...rest } = options;

  const res = await fetch(buildUrl(url, params), {
    ...rest,
    credentials: 'include', // cookie сессии
    headers: { 'Content-Type': 'application/json', ...(headers || {}) },
    ...(body !== undefined
      ? { body: typeof body === 'string' ? body : JSON.stringify(body) }
      : {}),
  });

  // Сессия истекла. Бэк может отвечать 401 или отдавать HTML страницы входа —
  // уточнить у бэкенда и при необходимости добавить редирект на форму входа.
  if (res.status === 401 || res.status === 403) {
    const err = new Error('Сессия истекла, войдите заново');
    err.status = res.status;
    err.unauthorized = true;
    throw err;
  }

  const ct = res.headers.get('content-type') || '';
  const data = ct.includes('application/json')
    ? await res.json().catch(() => null)
    : await res.text().catch(() => null);

  if (!res.ok) {
    const msg = (data && typeof data === 'object' && (data.error || data.message))
      || `Ошибка ${res.status}`;
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }

  if (!withTotal) return data;

  const items = Array.isArray(data) ? data : [];
  const raw = res.headers.get('X-Total-Count');
  const total = raw != null && raw !== '' ? parseInt(raw, 10) : items.length;
  return { items, total: Number.isFinite(total) ? total : items.length };
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
  // TODO: подключить, когда придут контракты
  clients: (params) => request(ENDPOINTS.clients, { params, withTotal: true }),
  updateClient: (id, body) => request(`${ENDPOINTS.client}/${id}`, { method: 'PATCH', body }),
  orders: (params) => request(ENDPOINTS.orders, { params, withTotal: true }),
};

/* --- Справочники и форматирование --- */

// TODO: уточнить полный список у бэкенда (в ТЗ сказано «на случай новых статусов»).
export const CLIENT_STATUS = {
  ACTIVE: 'Активен',
  BLOCKED: 'Заблокирован',
};

export const statusLabel = (s) => CLIENT_STATUS[s] || s || '—';

export const fmtAmount = (n) =>
  n == null || n === '' ? '—' : Number(n).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

// Даты приходят с бэка готовыми строками — форматирование не требуется.
export const fmtDate = (s) => s || '—';
