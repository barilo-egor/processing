import { useCallback, useEffect, useRef, useState } from 'react';
import {
  api, isUuid, fmtDateTime, fmtPercent, fmtMoney,
  dateTimeToMs, shortValue, fmtRate,
} from './api.js';

/* ============================================================
   Личный кабинет администратора API.

   Разделы переключаются состоянием, без роутера: адрес в строке
   браузера не меняется. Если понадобятся отдельные адреса —
   добавим react-router.
   ============================================================ */

const PAGE_SIZE = 25;

/* Границы «Времени активности ордеров».
   ВНИМАНИЕ: в ТЗ указано «минимум 120, максимум 120» и текст ошибки
   «Укажите целое число от 120до120» — это незаполненные заглушки.
   API разрешает от 0, у существующих клиентов стоит 900.
   Уточняется у автора ТЗ; поменять здесь — текст ошибки соберётся сам. */
const TIMEOUT_MIN = 120;
const TIMEOUT_MAX = 86400;

const SECTIONS = [
  { id: 'clients', title: 'Клиенты', icon: 'fa-solid fa-users' },
  { id: 'orders', title: 'Ордера', icon: 'fa-solid fa-receipt' },
  { id: 'merchants', title: 'Конфигурация мерчантов', icon: 'fa-solid fa-sliders' },
  { id: 'withdrawals', title: 'Заявки на вывод', icon: 'fa-solid fa-money-bill-transfer' },
];

export default function App() {
  const [section, setSection] = useState('clients');
  const [toast, setToast] = useState(null);
  const [orderStatuses, setOrderStatuses] = useState([]);
  // Справочник мерчантов: name -> displayName для показа человеку.
  const [merchants, setMerchants] = useState([]);
  // Способы оплаты: CARD -> «Карта» и так далее.
  const [methods, setMethods] = useState([]);
  // Статусы заявок на вывод: NEW -> «Новая» и так далее.
  const [withdrawalStatuses, setWithdrawalStatuses] = useState([]);
  // Клиент, выбранный переходом из таблицы в конфигурацию мерчантов.
  const [configClient, setConfigClient] = useState(null);

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type, key: Date.now() });
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

  // Справочники — из словаря бэка.
  useEffect(() => {
    api.dictionary()
        .then((d) => {
          // В ответе бэка ключ называется OderStatus — опечатка на их стороне,
          // поэтому читаем оба варианта.
          const orders = d?.OrderStatus || d?.OderStatus;
          if (Array.isArray(orders) && orders.length) setOrderStatuses(orders);
          const merchants = d?.Merchant;
          if (Array.isArray(merchants) && merchants.length) setMerchants(merchants);
          const methods = d?.RequestMethod;
          if (Array.isArray(methods) && methods.length) setMethods(methods);
          const wStatuses = d?.WithdrawalRequestStatus;
          if (Array.isArray(wStatuses) && wStatuses.length) setWithdrawalStatuses(wStatuses);
        })
        .catch(() => { /* без словаря покажутся коды значений */ });
  }, []);

  const openMerchantConfig = (client) => {
    setConfigClient(client);
    setSection('merchants');
  };

  const current = SECTIONS.find((s) => s.id === section);

  return (
      <div className="layout">
        <aside className="sidebar">
          <div className="brand">
            <span className="brand-ico"><i className="fa-solid fa-shield-halved" /></span>
            <span className="brand-name">Кабинет API</span>
          </div>
          <nav className="nav">
            {SECTIONS.map((s) => (
                <button
                    key={s.id}
                    type="button"
                    className={`nav-item${section === s.id ? ' active' : ''}`}
                    onClick={() => setSection(s.id)}
                >
                  <i className={s.icon} />
                  <span>{s.title}</span>
                </button>
            ))}
          </nav>
        </aside>

        <div className="main">
          <header className="topbar">
            <h1>{current.title}</h1>
            <AccountMenu />
          </header>
          <main className="content">
            {section === 'clients' && (
                <ClientsSection
                    methods={methods}
                    showToast={showToast}
                    onOpenMerchantConfig={openMerchantConfig}
                />
            )}
            {section === 'orders' && (
                <OrdersSection
                    orderStatuses={orderStatuses}
                    merchants={merchants}
                    methods={methods}
                    showToast={showToast}
                />
            )}
            {section === 'merchants' && (
                <MerchantsSection
                    client={configClient}
                    showToast={showToast}
                    onPickClient={setConfigClient}
                />
            )}
            {section === 'withdrawals' && (
                <WithdrawalsSection
                    withdrawalStatuses={withdrawalStatuses}
                    showToast={showToast}
                />
            )}
          </main>
        </div>

        {toast && <div className={`toast toast-${toast.type}`}>{toast.message}</div>}
      </div>
  );
}

/* ==================== Раздел «Клиенты» ==================== */

const EMPTY_FILTER = {
  id: '', username: '',
  dateMode: 'equal', dateEqual: '', dateFrom: '', dateTo: '',
  // Время необязательно: без него поиск идёт по дате, как раньше.
  timeEqual: '', timeFrom: '', timeTo: '',
};

function ClientsSection({ methods, showToast, onOpenMerchantConfig }) {
  const [draft, setDraft] = useState(EMPTY_FILTER);   // что набрано в полях
  const [applied, setApplied] = useState(EMPTY_FILTER); // что реально отправлено
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [firstLoad, setFirstLoad] = useState(true);
  const [card, setCard] = useState(null); // открытый клиент

  const idInvalid = draft.id.trim() !== '' && !isUuid(draft.id);

  const load = useCallback(async (f, p) => {
    setLoading(true);
    try {
      const params = { page: p, size: PAGE_SIZE };
      if (f.id.trim()) params.id = f.id.trim();
      if (f.username.trim()) params.username = f.username.trim();
      // Даты уходят как UNIX-время в миллисекундах. «Равна» без времени —
      // весь день, со временем — ровно эта секунда.
      if (f.dateMode === 'equal') {
        if (f.dateEqual) {
          params.from = dateTimeToMs(f.dateEqual, f.timeEqual, 'start');
          params.to = dateTimeToMs(f.dateEqual, f.timeEqual, 'end');
        }
      } else {
        if (f.dateFrom) params.from = dateTimeToMs(f.dateFrom, f.timeFrom, 'start');
        if (f.dateTo) params.to = dateTimeToMs(f.dateTo, f.timeTo, 'end');
      }

      const { items, total: t } = await api.clients(params);
      setRows(items);
      setTotal(t);
    } catch (e) {
      showToast(e.message || 'Не удалось загрузить клиентов', 'error');
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page); }, [load, applied, page]);

  const setF = (k, v) => setDraft((p) => ({ ...p, [k]: v }));

  const search = () => {
    if (idInvalid) return;
    setApplied(draft);
    setPage(0);
  };
  const reset = () => {
    setDraft(EMPTY_FILTER);
    setApplied({ ...EMPTY_FILTER });
    setPage(0);
  };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  // Клиент изменился в карточке — обновляем и карточку, и строку таблицы.
  const applyUpdated = (updated) => {
    setCard(updated);
    setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
  };

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid">
            <div className="field">
              <label htmlFor="f-id">ID</label>
              <input
                  id="f-id" type="text" placeholder="UUID клиента"
                  className={idInvalid ? 'invalid' : ''}
                  value={draft.id}
                  onChange={(e) => setF('id', e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && search()}
              />
              {idInvalid && <span className="field-error">ID должен соответствовать формату UUID</span>}
            </div>

            <div className="field">
              <label htmlFor="f-login">Логин</label>
              <input
                  id="f-login" type="text" placeholder="Логин клиента"
                  value={draft.username}
                  onChange={(e) => setF('username', e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && search()}
              />
            </div>

            {/* Режим и сами даты — в одном блоке, чтобы сетка не разносила их по строкам.
                В диапазоне две пары «дата + время», поэтому блок занимает всю строку. */}
            <div className={`field-pair${draft.dateMode === 'range' ? ' field-pair-range' : ''}`}>
              <div className="field">
                <label htmlFor="f-mode">Дата регистрации</label>
                <select id="f-mode" value={draft.dateMode} onChange={(e) => setF('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>
              {draft.dateMode === 'equal' ? (
                  <DateTimeField
                      id="f-date" label="Дата"
                      date={draft.dateEqual} time={draft.timeEqual}
                      onDate={(v) => setF('dateEqual', v)} onTime={(v) => setF('timeEqual', v)}
                  />
              ) : (
                  <>
                    <DateTimeField
                        id="f-from" label="С"
                        date={draft.dateFrom} time={draft.timeFrom}
                        onDate={(v) => setF('dateFrom', v)} onTime={(v) => setF('timeFrom', v)}
                    />
                    <DateTimeField
                        id="f-to" label="По"
                        date={draft.dateTo} time={draft.timeTo}
                        onDate={(v) => setF('dateTo', v)} onTime={(v) => setF('timeTo', v)}
                    />
                  </>
              )}
            </div>
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={idInvalid}>
              <i className="fa-solid fa-magnifying-glass" /> Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset}>Сбросить</button>
          </div>
        </div>

        {loading && firstLoad ? (
            <div className="state"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
        ) : rows.length === 0 ? (
            <div className="state"><i className="fa-solid fa-users-slash" /> Клиентов не найдено.</div>
        ) : (
            <>
              <div className="table-wrap">
                <table className="grid">
                  <thead>
                  <tr>
                    <th>ID</th>
                    <th>Логин</th>
                    <th>Дата регистрации</th>
                    <th className="c-right">Комиссия</th>
                    <th className="c-act" aria-label="Действия" />
                  </tr>
                  </thead>
                  <tbody>
                  {rows.map((c) => (
                      <tr key={c.id} onDoubleClick={() => setCard(c)}>
                        <td className="c-id">
                      <span className="copyable" onClick={(e) => { e.stopPropagation(); copy(c.id); }} title="Скопировать">
                        <span className="mono">{c.id}</span> <i className="fa-regular fa-copy" />
                      </span>
                        </td>
                        <td>{c.username || '—'}</td>
                        <td className="mono">{fmtDateTime(c.registeredAt)}</td>
                        <td className="c-right mono">{fmtPercent(c.commissionPercent)}</td>
                        <td className="c-act">
                          <button
                              type="button" className="icon-btn" title="Конфигурация мерчантов"
                              onClick={(e) => { e.stopPropagation(); onOpenMerchantConfig(c); }}
                          >
                            <i className="fa-solid fa-sliders" />
                          </button>
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>

              <Pagination
                  page={page} total={total} busy={loading}
                  onPage={setPage} label="Всего клиентов"
              />
            </>
        )}

        {card && (
            <ClientCard
                client={card}
                methods={methods}
                onClose={() => setCard(null)}
                onUpdated={applyUpdated}
                onCopy={copy}
                showToast={showToast}
            />
        )}
      </>
  );
}

/* ---- Пагинация ---- */
function Pagination({ page, total, onPage, busy, label }) {
  if (!total) return null;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  return (
      <div className="pagination">
        <span className="pagination-info">{label}: {total.toLocaleString('ru-RU')}</span>
        <div className="pagination-controls">
          <button type="button" className="page-btn" title="Первая"
                  disabled={busy || page <= 0} onClick={() => onPage(0)}>
            <i className="fa-solid fa-angles-left" />
          </button>
          <button type="button" className="page-btn" title="Назад"
                  disabled={busy || page <= 0} onClick={() => onPage(page - 1)}>
            <i className="fa-solid fa-chevron-left" />
          </button>
          <span className="page-current">{page + 1} / {pageCount}</span>
          <button type="button" className="page-btn" title="Вперёд"
                  disabled={busy || page >= pageCount - 1} onClick={() => onPage(page + 1)}>
            <i className="fa-solid fa-chevron-right" />
          </button>
          <button type="button" className="page-btn" title="Последняя"
                  disabled={busy || page >= pageCount - 1} onClick={() => onPage(pageCount - 1)}>
            <i className="fa-solid fa-angles-right" />
          </button>
        </div>
      </div>
  );
}


/* ==================== Меню аккаунта ==================== */
/* Логин администратора в правом углу верхней панели.
   Клик раскрывает меню с выходом; повторный клик или клик вне меню закрывает.

   ВНИМАНИЕ: эндпоинта «текущий пользователь» в коллекции нет, поэтому
   логин пока не запрашивается — показывается общая подпись. Как только
   бэк отдаст его (например, GET /api/private/support-user/current),
   достаточно подставить значение в состояние name. */
function AccountMenu() {
  const [open, setOpen] = useState(false);
  const [name] = useState('Администратор');
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDocDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDocDown);
    window.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
      <div className="account" ref={ref}>
        <button
            type="button"
            className={`account-btn${open ? ' is-open' : ''}`}
            onClick={() => setOpen((v) => !v)}
            aria-haspopup="menu"
            aria-expanded={open}
        >
          <span className="account-name">{name}</span>
          <i className={`fa-solid fa-chevron-${open ? 'up' : 'down'} account-caret`} />
        </button>

        {open && (
            <div className="account-menu" role="menu">
              <a className="account-item" href="/logout" role="menuitem">
                <i className="fa-solid fa-right-from-bracket" />
                Выйти
              </a>
            </div>
        )}
      </div>
  );
}

/* ==================== Раздел «Ордера» ==================== */
/* Только просмотр: список с фильтром и карточка по двойному клику. */

const EMPTY_ORDER_FILTER = {
  id: '', client: '', internalId: '', merchant: '', merchantOrderId: '',
  status: '',
  // Дата создания: «Равна» — одна дата, «Диапазон» — две.
  // Время у каждой даты необязательно.
  dateMode: 'equal',
  date: '', time: '',
  createdAtFrom: '', createdAtFromTime: '',
  createdAtTo: '', createdAtToTime: '',
};

function OrdersSection({ orderStatuses, merchants, methods, showToast }) {
  const [draft, setDraft] = useState(EMPTY_ORDER_FILTER);
  const [applied, setApplied] = useState(EMPTY_ORDER_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [card, setCard] = useState(null);

  const load = useCallback(async (filter, pageNum) => {
    setLoading(true);
    try {
      // «Равна» без времени — весь день, со временем — ровно эта секунда.
      const range = filter.dateMode === 'equal'
          ? {
            createdAtFrom: dateTimeToMs(filter.date, filter.time, 'start'),
            createdAtTo: dateTimeToMs(filter.date, filter.time, 'end'),
          }
          : {
            createdAtFrom: dateTimeToMs(filter.createdAtFrom, filter.createdAtFromTime, 'start'),
            createdAtTo: dateTimeToMs(filter.createdAtTo, filter.createdAtToTime, 'end'),
          };
      const r = await api.orders({
        page: pageNum,
        size: PAGE_SIZE,
        id: filter.id.trim(),
        // Бэк принимает одно поле client — ищет и по логину, и по ID.
        client: filter.client.trim(),
        internalId: filter.internalId.trim(),
        merchant: filter.merchant,
        merchantOrderId: filter.merchantOrderId.trim(),
        status: filter.status,
        ...range,
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить ордера', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page); }, [applied, page, load]);

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const search = () => { setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setDraft(EMPTY_ORDER_FILTER); setApplied(EMPTY_ORDER_FILTER); };
  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));

  const statusLabel = (name) =>
      orderStatuses.find((s) => s.name === name)?.description || name || '—';

  // Мерчанты приходят кодом (ALFA_TEAM), человеку показываем displayName.
  const merchantLabel = (name) =>
      merchants.find((m) => m.name === name)?.displayName || name || '—';

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid">
            <div className="field">
              <label htmlFor="o-id">ID ордера</label>
              <input id="o-id" value={draft.id} placeholder="UUID ордера"
                     onChange={(e) => set('id', e.target.value)} />
            </div>

            <div className="field">
              <label htmlFor="o-client">Клиент</label>
              <input id="o-client" value={draft.client} placeholder="Логин или ID клиента"
                     onChange={(e) => set('client', e.target.value)} />
            </div>

            <div className="field">
              <label htmlFor="o-internal">ID в системе клиента</label>
              <input id="o-internal" value={draft.internalId}
                     onChange={(e) => set('internalId', e.target.value)} />
            </div>

            {/* В ТЗ здесь текстовое поле, но сервер фильтрует по коду
              (EVO_PAY), а в таблице показывается название (EvoPay) —
              вводить пришлось бы код. Справочник мерчантов уже загружен,
              поэтому выбираем из списка. */}
            <div className="field">
              <label htmlFor="o-merchant">Мерчант</label>
              <select id="o-merchant" value={draft.merchant}
                      onChange={(e) => set('merchant', e.target.value)}>
                <option value="">Все</option>
                {merchants.map((m) => (
                    <option key={m.name} value={m.name}>{m.displayName || m.name}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="o-morder">ID у мерчанта</label>
              <input id="o-morder" value={draft.merchantOrderId}
                     onChange={(e) => set('merchantOrderId', e.target.value)} />
            </div>

            <div className="field">
              <label htmlFor="o-status">Статус</label>
              <select id="o-status" value={draft.status}
                      onChange={(e) => set('status', e.target.value)}>
                <option value="">Все</option>
                {orderStatuses.map((s) => (
                    <option key={s.name} value={s.name}>{s.description}</option>
                ))}
              </select>
            </div>

            {/* Режим и даты — одним блоком; в диапазоне он занимает всю строку */}
            <div className={`field-pair${draft.dateMode === 'range' ? ' field-pair-range' : ''}`}>
              <div className="field">
                <label htmlFor="o-mode">Дата создания</label>
                <select id="o-mode" value={draft.dateMode}
                        onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>
              {draft.dateMode === 'equal' ? (
                  <DateTimeField
                      id="o-date" label="Дата"
                      date={draft.date} time={draft.time}
                      onDate={(v) => set('date', v)} onTime={(v) => set('time', v)}
                  />
              ) : (
                  <>
                    <DateTimeField
                        id="o-from" label="С"
                        date={draft.createdAtFrom} time={draft.createdAtFromTime}
                        onDate={(v) => set('createdAtFrom', v)} onTime={(v) => set('createdAtFromTime', v)}
                    />
                    <DateTimeField
                        id="o-to" label="По"
                        date={draft.createdAtTo} time={draft.createdAtToTime}
                        onDate={(v) => set('createdAtTo', v)} onTime={(v) => set('createdAtToTime', v)}
                    />
                  </>
              )}
            </div>
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={loading}>
              <i className="fa-solid fa-magnifying-glass" />
              Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset} disabled={loading}>
              Сбросить
            </button>
          </div>
        </div>

        <p className="table-hint">Двойной клик по строке — подробная информация</p>

        <div className="table-wrap">
          <table className="grid grid-orders">
            <thead>
            <tr>
              <th>ID ордера</th>
              <th>ID в системе клиента</th>
              <th>Клиент</th>
              <th className="c-right">Сумма, руб.</th>
              <th>Статус</th>
              <th>Создан</th>
              <th>Мерчант</th>
              <th>ID у мерчанта</th>
            </tr>
            </thead>
            <tbody>
            {rows.map((o) => (
                <tr key={o.id} onDoubleClick={() => setCard(o)} title="Двойной клик — карточка ордера">
                  <td className="c-id">
                  <span className="copyable" onClick={(e) => { e.stopPropagation(); copy(o.id); }} title="Скопировать">
                    <span className="mono">{shortId(o.id)}</span> <i className="fa-regular fa-copy" />
                  </span>
                  </td>
                  <td className="mono">{o.internalId || '—'}</td>
                  <td>
                    <span className="cell-main">{o.clientUsername || '—'}</span>
                    <span className="cell-sub mono">{o.clientId || '—'}</span>
                  </td>
                  <td className="c-right mono">{fmtAmount(o.amount)}</td>
                  <td>
                  <span className={`badge badge-${orderStatusMod(o.status)}`}>
                    {statusLabel(o.status)}
                  </span>
                  </td>
                  <td className="mono">{fmtDateTime(o.createdAt)}</td>
                  <td>{merchantLabel(o.merchant)}</td>
                  <td className="mono">{o.merchantOrderId || '—'}</td>
                </tr>
            ))}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Ордеров не найдено</div>
          )}
          {loading && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего ордеров"
        />

        {card && (
            <OrderCard
                order={card}
                orderStatuses={orderStatuses}
                merchants={merchants}
                methods={methods}
                onClose={() => setCard(null)}
                onCopy={copy}
                showToast={showToast}
            />
        )}
      </>
  );
}

/* ==================== Карточка ордера ==================== */
/* Всё только для чтения. Ключевые значения — сумма и статус —
   вынесены отдельной строкой, остальное в двух колонках. */

function OrderCard({ order, orderStatuses, merchants, methods, onClose, onCopy, showToast }) {
  const [client, setClient] = useState(null);   // карточка клиента поверх
  const [loadingClient, setLoadingClient] = useState(false);
  /* В списке приходит сокращённый набор полей, поэтому подробности
     (метод, банк, реквизиты, срок, статус у мерчанта) дозапрашиваем.
     До ответа показываем то, что уже есть из строки таблицы. */
  const [full, setFull] = useState(order);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api.order(order.id)
        .then((d) => { if (alive && d) setFull({ ...order, ...d }); })
        .catch((e) => {
          if (alive) showToast(e.message || 'Не удалось загрузить ордер', 'error');
        })
        .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [order, showToast]);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !client) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, client]);

  const statusLabel = orderStatuses.find((s) => s.name === full.status)?.description
      || full.status || '—';
  const merchantLabel = merchants.find((m) => m.name === full.merchant)?.displayName
      || full.merchant || '—';
  const methodLabel = methods.find((m) => m.name === full.method)?.description
      || full.method || '—';

  // Клиента в ордере нет целиком — только логин и ID, поэтому
  // перед открытием карточки подтягиваем запись.
  const openClient = async () => {
    if (!order.clientId) {
      showToast('У ордера не указан клиент', 'error');
      return;
    }
    setLoadingClient(true);
    try {
      const r = await api.clients({ id: order.clientId, page: 0, size: 1 });
      const found = r.items[0];
      if (!found) {
        showToast('Клиент не найден', 'error');
        return;
      }
      setClient(found);
    } catch (e) {
      showToast(e.message || 'Не удалось открыть карточку клиента', 'error');
    } finally {
      setLoadingClient(false);
    }
  };

  return (
      <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
        <div className="modal modal-wide">
          <div className="ocard-head">
            <span className="ocard-ico"><i className="fa-solid fa-receipt" /></span>
            <div className="ocard-title">
              <span className="eyebrow">Ордер</span>
              <span className="copyable mono ocard-id" onClick={() => onCopy(full.id)} title="Скопировать">
              {full.id} <i className="fa-regular fa-copy" />
            </span>
            </div>
            {loading && <i className="fa-solid fa-spinner fa-spin head-spin" />}
            <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
          </div>

          {/* Ключевые значения: сумма слева, статус справа */}
          <div className="key-row">
            <div className="key-item">
              <span className="eyebrow">Сумма</span>
              <span className="key-value mono">{fmtAmount(full.amount)} ₽</span>
            </div>
            <div className="key-item key-item-right">
              <span className="eyebrow">Статус</span>
              <span className={`badge badge-${orderStatusMod(full.status)}`}>{statusLabel}</span>
            </div>
          </div>

          <div className="modal-body">
            <div className="two-col">
              <section className="col">
                <Field label="Логин клиента">{full.clientUsername || '—'}</Field>

                <Field label="ID клиента">
                <span className="copyable" onClick={() => onCopy(full.clientId)} title="Скопировать">
                  <span className="mono">{full.clientId || '—'}</span> <i className="fa-regular fa-copy" />
                </span>
                </Field>

                <Field label="ID в системе клиента">
                <span className="copyable" onClick={() => onCopy(full.internalId)} title="Скопировать">
                  <span className="mono">{full.internalId || '—'}</span> <i className="fa-regular fa-copy" />
                </span>
                </Field>

                <Field label="Метод">{methodLabel}</Field>
                <Field label="Банк">{full.bank || '—'}</Field>

                <Field label="Реквизиты">
                <span className="copyable" onClick={() => onCopy(full.details)} title="Скопировать">
                  <span className="mono">{full.details || '—'}</span> <i className="fa-regular fa-copy" />
                </span>
                </Field>
              </section>

              <section className="col">
                <Field label="Дата создания">
                  <span className="mono">{fmtDateTime(full.createdAt)}</span>
                </Field>
                <Field label="Дата истечения">
                  <span className="mono">{fmtDateTime(full.expiresAt)}</span>
                </Field>
                <Field label="Уникализация суммы">
                  {full.enableUniqueAmount ? 'Разрешена' : 'Не разрешена'}
                </Field>
                <Field label="Callback URL">
                  <span className="mono wrap">{full.callbackUrl || '—'}</span>
                </Field>
                <Field label="Мерчант">{merchantLabel}</Field>

                <Field label="ID у мерчанта">
                <span className="copyable" onClick={() => onCopy(full.merchantOrderId)} title="Скопировать">
                  <span className="mono">{full.merchantOrderId || '—'}</span> <i className="fa-regular fa-copy" />
                </span>
                </Field>

                <Field label="Статус у мерчанта">{full.merchantOrderStatus || '—'}</Field>
              </section>
            </div>
          </div>

          <div className="modal-foot modal-foot-split">
            <button type="button" className="btn btn-secondary" onClick={openClient} disabled={loadingClient}>
              <i className="fa-solid fa-user" />
              Открыть карточку клиента
            </button>
            <button type="button" className="btn btn-primary" onClick={onClose}>Закрыть</button>
          </div>
        </div>

        {client && (
            <ClientCard
                client={client}
                methods={methods}
                onClose={() => setClient(null)}
                onUpdated={setClient}
                onCopy={onCopy}
                showToast={showToast}
            />
        )}
      </div>
  );
}

/* ---- Поле карточки: подпись сверху, значение снизу ---- */
function Field({ label, children }) {
  return (
      <div className="fitem">
        <span className="eyebrow">{label}</span>
        <span className="fvalue">{children}</span>
      </div>
  );
}

/* Цвет бейджа статуса ордера. Успешный — зелёный, отменённый —
   красный, просроченный и спорный — жёлтый, новый — нейтральный. */
function orderStatusMod(name) {
  if (name === 'SUCCESS') return 'ok';
  if (name === 'CANCELED') return 'bad';
  if (name === 'TIMEOUT' || name === 'DISPUTE') return 'warn';
  return 'info';
}

/* Сумма ордера: целые рубли с разделителем разрядов. */
function fmtAmount(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  return Number.isFinite(n) ? n.toLocaleString('ru-RU') : '—';
}

/* Укороченный UUID для таблицы: целиком он занимает всю ширину. */
function shortId(id) {
  const s = String(id || '');
  return s.length > 13 ? `${s.slice(0, 8)}…${s.slice(-4)}` : (s || '—');
}

/* ==================== Карточка клиента ==================== */
/* Карточка разделена на два блока: «Информация» только для чтения
   и «Настройки» с полями, доступными для правки сразу, без карандаша.
   Изменения применяются одной кнопкой «Сохранить» — сразу по всем
   изменённым полям, одним запросом.

   Статуса у клиента больше нет: эти данные ведутся на сервере
   авторизации. После «Времени активности ордеров» — поле «Методы»:
   весь список берётся из словаря RequestMethod, привязаны те,
   что пришли в client.methods.

   «Отмена» закрывает окно без сохранения, после «Сохранить»
   окно тоже закрывается. */

function ClientCard({ client, methods, onClose, onUpdated, onCopy, showToast }) {
  // Черновик настроек. Строки, а не числа: поле ввода всегда работает
  // со строкой, приведение и проверка — при сохранении.
  const initial = () => ({
    commission: client.commissionPercent ?? '',
    timeout: client.orderTimeoutSeconds ?? '',
    methods: Array.isArray(client.methods) ? client.methods : [],
  });

  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  // Клиент мог обновиться снаружи (например, после сохранения) —
  // подхватываем новые значения.
  const methodsKey = (client.methods || []).join(',');
  useEffect(() => { setForm(initial()); setErrors({}); },
      // eslint-disable-next-line react-hooks/exhaustive-deps
      [client.id, client.commissionPercent, client.orderTimeoutSeconds, methodsKey]);

  // Раскрыт ли список методов: Escape тогда закрывает только его.
  const [methodsOpen, setMethodsOpen] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !methodsOpen) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, methodsOpen]);

  const setField = (key, v) => {
    setForm((prev) => ({ ...prev, [key]: v }));
    setErrors((prev) => (prev[key] ? { ...prev, [key]: '' } : prev));
  };

  const setMethods = (list) => setForm((prev) => ({ ...prev, methods: list }));

  /* Проверка полей. Возвращает { body, errors }:
     body — только изменённые поля, errors — сообщения под полями. */
  const validate = () => {
    const errs = {};
    const body = {};

    // Комиссия: число от 0 без верхней границы, точность до десятых.
    const rawCommission = String(form.commission).replace(',', '.').trim();
    const commission = Number(rawCommission);
    if (rawCommission === '' || !Number.isFinite(commission) || commission < 0) {
      errs.commission = 'Укажите число от 0';
    } else {
      const rounded = Math.round(commission * 10) / 10;
      if (rounded !== Number(client.commissionPercent)) body.commissionPercent = rounded;
    }

    // Время активности ордеров: целое в допустимом диапазоне.
    const timeout = Number(String(form.timeout).trim());
    if (!Number.isInteger(timeout) || timeout < TIMEOUT_MIN || timeout > TIMEOUT_MAX) {
      errs.timeout = `Укажите целое число от ${TIMEOUT_MIN} до ${TIMEOUT_MAX}`;
    } else if (timeout !== Number(client.orderTimeoutSeconds)) {
      body.orderTimeoutSeconds = timeout;
    }

    // Способы оплаты: шлём весь выбранный набор, только если он изменился.
    // Пустой массив отвязывает все методы, отсутствие поля — ничего не меняет.
    const was = [...(client.methods || [])].sort().join(',');
    const now = [...form.methods].sort().join(',');
    if (was !== now) body.methods = form.methods;

    return { body, errors: errs };
  };

  const onSave = async () => {
    const { body, errors: errs } = validate();

    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }
    if (!Object.keys(body).length) {
      showToast('Изменений нет', 'info');
      return;
    }

    setSaving(true);
    try {
      const updated = await api.updateClient(client.id, body);
      onUpdated(updated);
      showToast('Изменения сохранены', 'success');
      onClose();
    } catch (e) {
      showToast(e.message || 'Не удалось сохранить', 'error');
    } finally {
      setSaving(false);
    }
  };

  // Весь список — из словаря. Если словарь не загрузился, покажем хотя бы
  // привязанные методы кодами, чтобы не потерять их при сохранении.
  const allMethods = methods.length
      ? methods
      : (client.methods || []).map((name) => ({ name, description: name }));

  return (
      <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
        <div className="modal modal-wide">
          <div className="ocard-head">
          <span className="ocard-ico ocard-ico-lg">
            {(client.username || '?').charAt(0).toUpperCase()}
          </span>
            <div className="ocard-title">
              <span className="ocard-name">{client.username || '—'}</span>
              <span className="ocard-sub">Клиент API</span>
            </div>
            <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
          </div>

          <div className="modal-body">
            <div className="two-col two-col-split">
              {/* ---------- Информация: только чтение ---------- */}
              <section className="col">
                <h3 className="block-head">
                  <i className="fa-solid fa-circle-info" />
                  Информация
                </h3>

                <Field label="ID">
                <span className="copyable" onClick={() => onCopy(client.id)} title="Скопировать">
                  <span className="mono wrap">{client.id}</span> <i className="fa-regular fa-copy" />
                </span>
                </Field>

                <Field label="Логин">
                <span className="copyable" onClick={() => onCopy(client.username)} title="Скопировать">
                  {client.username || '—'} <i className="fa-regular fa-copy" />
                </span>
                </Field>

                <Field label="Дата регистрации">
                  <span className="mono">{fmtDateTime(client.registeredAt)}</span>
                </Field>

                <Field label="Баланс">
                  <span className="mono">{fmtMoney(client.balance)}</span>
                </Field>
              </section>

              {/* ---------- Настройки: поля правятся сразу ---------- */}
              <section className="col">
                <h3 className="block-head">
                  <i className="fa-solid fa-sliders" />
                  Настройки
                </h3>

                <FieldRow label="Комиссия" error={errors.commission}>
                <span className="input-suffix">
                  <input
                      type="number" step="0.1" min="0"
                      value={form.commission}
                      disabled={saving}
                      className={errors.commission ? 'invalid' : ''}
                      onChange={(e) => setField('commission', e.target.value)}
                  />
                  <span className="suffix">%</span>
                </span>
                </FieldRow>

                <FieldRow label="Время активности ордеров" error={errors.timeout}>
                <span className="input-suffix">
                  <input
                      type="number" step="1" min={TIMEOUT_MIN} max={TIMEOUT_MAX}
                      value={form.timeout}
                      disabled={saving}
                      className={errors.timeout ? 'invalid' : ''}
                      onChange={(e) => setField('timeout', e.target.value)}
                  />
                  <span className="suffix">сек</span>
                </span>
                </FieldRow>

                <FieldRow label="Методы">
                  <MethodsSelect
                      options={allMethods}
                      value={form.methods}
                      disabled={saving}
                      open={methodsOpen}
                      onOpenChange={setMethodsOpen}
                      onChange={setMethods}
                  />
                </FieldRow>
              </section>
            </div>
          </div>

          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" disabled={saving} onClick={onClose}>
              Отмена
            </button>
            <button type="button" className="btn btn-primary" disabled={saving} onClick={onSave}>
              <i className="fa-solid fa-check" />
              Сохранить
            </button>
          </div>
        </div>
      </div>
  );
}

/* ---- Выбор методов: метки в поле + выпадающий список с чекбоксами ----
   Свёрнуто: привязанные методы метками, у каждой крестик — снимает
   метод, не раскрывая список. Метки в одну строку: поле держит одну
   высоту, а не поместившиеся сворачиваются в «…ещё {n}» с полным
   перечнем в подсказке. Клик по полю раскрывает список всех методов
   словаря. Изменения попадают в черновик карточки и сохраняются
   только её кнопкой «Сохранить». */

// Сколько символов помещается в поле. Каждая метка сверх текста
// занимает место под отступы и крестик — считаем его как 4 символа.
const METHODS_CHAR_LIMIT = 26;
const TAG_OVERHEAD = 4;

function MethodsSelect({ options, value, disabled, open, onOpenChange, onChange }) {
  const ref = useRef(null);

  // Клик вне поля и списка, Escape — сворачивают список.
  useEffect(() => {
    if (!open) return undefined;
    const onDocDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onOpenChange(false);
    };
    const onKey = (e) => { if (e.key === 'Escape') onOpenChange(false); };
    document.addEventListener('mousedown', onDocDown);
    window.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open, onOpenChange]);

  const label = (name) => options.find((o) => o.name === name)?.description || name;

  // Метки — в порядке словаря, а не в порядке кликов.
  const selected = options.filter((o) => value.includes(o.name)).map((o) => o.name);

  // Сколько меток помещается в строку.
  let used = 0;
  let visibleCount = 0;
  for (const name of selected) {
    const cost = label(name).length + TAG_OVERHEAD;
    if (used + cost > METHODS_CHAR_LIMIT) break;
    used += cost;
    visibleCount += 1;
  }
  // Хотя бы одна метка видна всегда, даже очень длинная.
  if (visibleCount === 0 && selected.length) visibleCount = 1;
  const visible = selected.slice(0, visibleCount);
  const hidden = selected.length - visible.length;

  const toggle = (name) => {
    onChange(value.includes(name) ? value.filter((m) => m !== name) : [...value, name]);
  };

  const remove = (e, name) => {
    e.stopPropagation(); // крестик не раскрывает список
    onChange(value.filter((m) => m !== name));
  };

  return (
      <div className={`msel${open ? ' is-open' : ''}${disabled ? ' is-disabled' : ''}`} ref={ref}>
        <div
            className="msel-field"
            role="button"
            tabIndex={disabled ? -1 : 0}
            aria-haspopup="listbox"
            aria-expanded={open}
            onClick={() => !disabled && onOpenChange(!open)}
            onKeyDown={(e) => {
              if (disabled) return;
              if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpenChange(!open); }
            }}
        >
          <span className="msel-tags">
            {selected.length === 0 && <span className="msel-empty">Методы не выбраны</span>}
            {visible.map((name) => (
                <span key={name} className="msel-tag">
                  {label(name)}
                  <button
                      type="button" className="msel-x"
                      aria-label={`Убрать «${label(name)}»`}
                      disabled={disabled}
                      onClick={(e) => remove(e, name)}
                  >
                    ×
                  </button>
                </span>
            ))}
            {hidden > 0 && (
                <span className="msel-more" title={selected.map(label).join(', ')}>
                  …ещё {hidden}
                </span>
            )}
          </span>
          <i className={`fa-solid fa-chevron-${open ? 'up' : 'down'} msel-caret`} />
        </div>

        {open && (
            <ul className="msel-list" role="listbox" aria-multiselectable="true">
              {options.length === 0 && <li className="msel-none">Методы не загружены</li>}
              {options.map((o) => (
                  <li key={o.name}>
                    <label className="msel-option">
                      <input
                          type="checkbox"
                          checked={value.includes(o.name)}
                          onChange={() => toggle(o.name)}
                      />
                      <span>{o.description || o.name}</span>
                    </label>
                  </li>
              ))}
            </ul>
        )}
      </div>
  );
}

/* ---- Строка с полем ввода и сообщением об ошибке ---- */
function FieldRow({ label, error, children }) {
  return (
      <div className={`fitem fitem-field${error ? ' has-error' : ''}`}>
        <span className="eyebrow">{label}</span>
        <span className="fvalue">{children}</span>
        {error && <span className="field-error">{error}</span>}
      </div>
  );
}

/* ==================== Конфигурация мерчантов ==================== */
/* Очерёдность (колонка «№», перетаскивание) пока не реализована:
   бэк не отдаёт позицию мерчанта и не принимает её в PATCH.
   Всё остальное из ТЗ работает: включение, суммы, поиск.
   Пагинации по 25 тоже нет — эндпоинт отдаёт плоский массив без страниц. */

function MerchantsSection({ client, showToast, onPickClient }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');   // что набрано в поле
  const [applied, setApplied] = useState(''); // что применено кнопкой

  const load = useCallback(async (clientId) => {
    setLoading(true);
    try {
      const d = await api.merchantConfigs(clientId);
      setRows(Array.isArray(d) ? d : []);
    } catch (e) {
      showToast(e.message || 'Не удалось загрузить конфигурацию', 'error');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    if (client?.id) load(client.id);
    else setRows([]);
  }, [client, load]);

  // Одна строка обновилась — заменяем её в списке, не перезагружая всё.
  const applyRow = (updated) => {
    setRows((prev) => prev.map((r) => (r.id === updated.id ? { ...r, ...updated } : r)));
  };

  const toggleOn = async (row) => {
    try {
      const updated = await api.updateMerchantConfig(row.id, { isOn: !row.isOn });
      applyRow(updated && typeof updated === 'object' ? updated : { ...row, isOn: !row.isOn });
      showToast(!row.isOn ? 'Мерчант включён' : 'Мерчант отключён', 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось изменить', 'error');
    }
  };

  const saveAmount = async (row, field, value) => {
    const n = Number(String(value).trim());
    if (!Number.isInteger(n) || n <= 0) return 'Укажите целое число больше нуля';
    const min = field === 'minAmount' ? n : Number(row.minAmount);
    const max = field === 'maxAmount' ? n : Number(row.maxAmount);
    if (Number.isFinite(min) && Number.isFinite(max) && min > max) {
      return 'Минимальная сумма не может быть больше максимальной';
    }
    if (n === Number(row[field])) return null; // без изменений — молча закрываем

    try {
      const updated = await api.updateMerchantConfig(row.id, { [field]: n });
      applyRow(updated && typeof updated === 'object' ? updated : { ...row, [field]: n });
      showToast('Изменения сохранены', 'success');
      return null;
    } catch (e) {
      return e.message || 'Не удалось сохранить';
    }
  };

  if (!client) {
    return (
        <>
          <ClientPicker showToast={showToast} onPick={onPickClient} />
          <div className="card placeholder">
            <i className="fa-solid fa-sliders" />
            <p>Выберите клиента, чтобы настроить мерчантов</p>
          </div>
        </>
    );
  }

  const list = applied
      ? rows.filter((r) => String(r.merchant || '').toLowerCase().includes(applied.toLowerCase()))
      : rows;

  return (
      <>
        <div className="card client-chip">
          <div>
            <div className="chip-label">Клиент</div>
            <div className="chip-name">{client.username}</div>
            <div className="chip-id mono">{client.id}</div>
          </div>
          <button type="button" className="btn btn-secondary" onClick={() => onPickClient(null)}>
            Другой клиент
          </button>
        </div>

        <div className="card filter-card">
          <div className="filter-grid">
            <div className="field">
              <label htmlFor="m-search">Поиск мерчанта</label>
              <input
                  id="m-search" type="text" placeholder="Название мерчанта"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && setApplied(search.trim())}
              />
            </div>
          </div>
          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={() => setApplied(search.trim())}>
              <i className="fa-solid fa-magnifying-glass" /> Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => { setSearch(''); setApplied(''); }}>
              Сбросить
            </button>
          </div>
        </div>

        {loading ? (
            <div className="state"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
        ) : list.length === 0 ? (
            <div className="state"><i className="fa-solid fa-store-slash" /> Мерчанты не найдены</div>
        ) : (
            <div className="table-wrap">
              <table className="grid">
                <thead>
                <tr>
                  <th className="c-act">Вкл.</th>
                  <th>Мерчант</th>
                  <th className="c-right">Мин. сумма</th>
                  <th className="c-right">Макс. сумма</th>
                </tr>
                </thead>
                <tbody>
                {list.map((r) => (
                    <tr key={r.id} className="no-hover">
                      <td className="c-act">
                        <label className="switch" title={r.isOn ? 'Отключить' : 'Включить'}>
                          <input type="checkbox" checked={!!r.isOn} onChange={() => toggleOn(r)} />
                          <span className="track" />
                          <span className="thumb" />
                        </label>
                      </td>
                      <td>{r.merchant || '—'}</td>
                      <AmountCell row={r} field="minAmount" onSave={saveAmount} />
                      <AmountCell row={r} field="maxAmount" onSave={saveAmount} />
                    </tr>
                ))}
                </tbody>
              </table>
            </div>
        )}
      </>
  );
}

/* ---- Ячейка суммы: редактируется по клику ---- */
function AmountCell({ row, field, onSave }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const ref = useRef(null);

  useEffect(() => { if (editing && ref.current) ref.current.focus(); }, [editing]);

  const open = () => { setValue(row[field] ?? ''); setError(''); setEditing(true); };

  const commit = async () => {
    const err = await onSave(row, field, value);
    if (err) { setError(err); return; } // остаёмся в режиме правки
    setEditing(false);
  };

  if (!editing) {
    return (
        <td className="c-right cell-edit" onClick={open} title="Изменить">
          <span className="mono">{row[field] == null ? '—' : Number(row[field]).toLocaleString('ru-RU')}</span>
        </td>
    );
  }

  return (
      <td className="c-right">
        <input
            ref={ref} type="number" className={`cell-input${error ? ' invalid' : ''}`}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === 'Enter') { e.preventDefault(); commit(); }
              if (e.key === 'Escape') { e.preventDefault(); setEditing(false); setError(''); }
            }}
        />
        {error && <span className="cell-error">{error}</span>}
      </td>
  );
}

/* ---- Поиск клиента по логину или ID ---- */
function ClientPicker({ showToast, onPick }) {
  const [q, setQ] = useState('');
  const [found, setFound] = useState([]);
  const [loading, setLoading] = useState(false);
  const [touched, setTouched] = useState(false);

  // Ищем и по логину, и по ID: бэк принимает их отдельными параметрами,
  // поэтому шлём два запроса и объединяем результат.
  useEffect(() => {
    const term = q.trim();
    if (term.length < 2) { setFound([]); setTouched(false); return undefined; }
    const t = setTimeout(async () => {
      setLoading(true);
      setTouched(true);
      try {
        const [byName, byId] = await Promise.all([
          api.clients({ username: term, page: 0, size: 10 }).catch(() => ({ items: [] })),
          isUuid(term) ? api.clients({ id: term, page: 0, size: 10 }).catch(() => ({ items: [] })) : { items: [] },
        ]);
        const map = new Map();
        [...byName.items, ...byId.items].forEach((c) => map.set(c.id, c));
        setFound([...map.values()]);
      } catch (e) {
        showToast(e.message || 'Не удалось найти клиента', 'error');
      } finally {
        setLoading(false);
      }
    }, 350); // не дёргаем бэк на каждую букву
    return () => clearTimeout(t);
  }, [q, showToast]);

  return (
      <div className="card client-picker">
        <div className="field">
          <label htmlFor="c-search">Клиент</label>
          <input
              id="c-search" type="text" placeholder="Логин или ID клиента"
              value={q} onChange={(e) => setQ(e.target.value)}
          />
        </div>

        {loading && <div className="picker-note">Поиск…</div>}
        {!loading && touched && found.length === 0 && <div className="picker-note">Клиенты не найдены</div>}

        {found.length > 0 && (
            <ul className="picker-list">
              {found.map((c) => (
                  <li key={c.id}>
                    <button type="button" onClick={() => onPick(c)}>
                      <span className="pl-name">{c.username}</span>
                      <span className="pl-id mono">{c.id}</span>
                    </button>
                  </li>
              ))}
            </ul>
        )}
      </div>
  );
}
/* ==================== Раздел «Заявки на вывод» ==================== */
/* Заявки всех клиентов: фильтр, таблица, подтверждение и отмена.
   Подробного просмотра нет. Кнопки действий — только у заявок NEW. */

const EMPTY_WD_FILTER = {
  id: '', client: '', status: '', address: '',
  dateMode: 'equal',
  date: '', time: '',
  dateFrom: '', timeFrom: '',
  dateTo: '', timeTo: '',
};

/* Цвет метки статуса заявки: NEW — синий, APPROVED — зелёный,
   CANCELED — красный. */
function withdrawalStatusMod(name) {
  if (name === 'APPROVED') return 'ok';
  if (name === 'CANCELED') return 'bad';
  if (name === 'NEW') return 'info';
  return 'neutral';
}

function WithdrawalsSection({ withdrawalStatuses, showToast }) {
  const [draft, setDraft] = useState(EMPTY_WD_FILTER);
  const [applied, setApplied] = useState(EMPTY_WD_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [confirm, setConfirm] = useState(null); // { row, action: 'approve' | 'cancel' }
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (f, pageNum) => {
    setLoading(true);
    try {
      // «Равна» — одна дата разворачивается в границы; без времени это
      // весь день, со временем — ровно эта секунда.
      const range = f.dateMode === 'equal'
          ? {
            createdAtFrom: dateTimeToMs(f.date, f.time, 'start'),
            createdAtTo: dateTimeToMs(f.date, f.time, 'end'),
          }
          : {
            createdAtFrom: dateTimeToMs(f.dateFrom, f.timeFrom, 'start'),
            createdAtTo: dateTimeToMs(f.dateTo, f.timeTo, 'end'),
          };
      const r = await api.withdrawals({
        page: pageNum,
        size: PAGE_SIZE,
        id: f.id.trim(),
        // Одно поле на логин и ID клиента — бэк ищет по обоим.
        client: f.client.trim(),
        status: f.status,
        address: f.address.trim(),
        ...range,
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить заявки', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page); }, [applied, page, reloadKey, load]);

  const reload = () => setReloadKey((k) => k + 1);

  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));
  const search = () => { setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setDraft(EMPTY_WD_FILTER); setApplied(EMPTY_WD_FILTER); };
  const onEnter = (e) => { if (e.key === 'Enter') search(); };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const statusLabel = (name) =>
      withdrawalStatuses.find((s) => s.name === name)?.description || name || '—';

  /* Подтверждение или отмена. Статус проверяется на сервере: если заявку
     уже обработали, бэк отвечает 400. Такой ответ не отличить от прочих
     ошибок по коду, поэтому перезапрашиваем заявку и смотрим её статус. */
  const process = async ({ row, action }) => {
    const next = action === 'approve' ? 'APPROVED' : 'CANCELED';
    setBusy(true);
    try {
      await api.setWithdrawalStatus(row.id, next);
      setRows((prev) => prev.map((r) => (r.id === row.id ? { ...r, status: next } : r)));
      showToast(action === 'approve' ? 'Заявка подтверждена' : 'Заявка отменена', 'success');
    } catch (e) {
      let changed = false;
      if (!e.unauthorized) {
        try {
          const r = await api.withdrawals({ id: row.id, page: 0, size: 1 });
          const fresh = r.items.find((x) => x.id === row.id);
          changed = Boolean(fresh && fresh.status !== 'NEW');
        } catch { /* проверить не удалось — покажем исходную ошибку */ }
      }
      if (changed) {
        showToast('Статус заявки уже изменён', 'error');
        reload();
      } else {
        showToast(e.message || 'Не удалось изменить статус заявки', 'error');
      }
    } finally {
      setBusy(false);
      setConfirm(null);
    }
  };

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid filter-grid-3">
            <div className="field">
              <label htmlFor="w-id">ID заявки</label>
              <input id="w-id" value={draft.id} placeholder="UUID заявки"
                     onChange={(e) => set('id', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="w-client">Клиент</label>
              <input id="w-client" value={draft.client} placeholder="ID или логин клиента"
                     onChange={(e) => set('client', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="w-status">Статус</label>
              <select id="w-status" value={draft.status} onChange={(e) => set('status', e.target.value)}>
                <option value="">Все</option>
                {withdrawalStatuses.map((s) => (
                    <option key={s.name} value={s.name}>{s.description}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="w-address">Адрес кошелька</label>
              <input id="w-address" value={draft.address} placeholder="Адрес USDT-кошелька"
                     onChange={(e) => set('address', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="w-mode">Дата создания</label>
              <select id="w-mode" value={draft.dateMode} onChange={(e) => set('dateMode', e.target.value)}>
                <option value="equal">Равна</option>
                <option value="range">Диапазон</option>
              </select>
            </div>

            {draft.dateMode === 'equal' ? (
                <DateTimeField
                    id="w-date" label="Дата"
                    date={draft.date} time={draft.time}
                    onDate={(v) => set('date', v)} onTime={(v) => set('time', v)}
                />
            ) : (
                <>
                  <DateTimeField
                      id="w-from" label="С"
                      date={draft.dateFrom} time={draft.timeFrom}
                      onDate={(v) => set('dateFrom', v)} onTime={(v) => set('timeFrom', v)}
                  />
                  <DateTimeField
                      id="w-to" label="По"
                      date={draft.dateTo} time={draft.timeTo}
                      onDate={(v) => set('dateTo', v)} onTime={(v) => set('timeTo', v)}
                  />
                </>
            )}
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={loading}>
              <i className="fa-solid fa-magnifying-glass" />
              Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset} disabled={loading}>
              Сбросить
            </button>
          </div>
        </div>

        <div className="table-wrap">
          <table className="grid grid-withdrawals">
            <thead>
            <tr>
              <th>Клиент</th>
              <th>ID заявки</th>
              <th>Статус</th>
              <th>Создана</th>
              <th className="c-right">Сумма заявки, ₽</th>
              <th className="c-right">Комиссия</th>
              <th className="c-right">К выводу, ₽</th>
              <th className="c-right">Курс ₽/USDT</th>
              <th className="c-right">Сумма, USDT</th>
              <th>Адрес кошелька</th>
              <th className="c-act" aria-label="Действия" />
            </tr>
            </thead>
            <tbody>
            {rows.map((w) => (
                <tr key={w.id} className="no-hover">
                  <td>
                    <span className="cell-main">{w.clientUsername || '—'}</span>
                    <span className="cell-sub">
                      <CopyValue value={w.clientId} onCopy={copy} />
                    </span>
                  </td>
                  <td className="c-id"><CopyValue value={w.id} onCopy={copy} /></td>
                  <td>
                    <span className={`badge badge-${withdrawalStatusMod(w.status)}`}>
                      {statusLabel(w.status)}
                    </span>
                  </td>
                  <td className="mono nowrap">{fmtDateTime(w.createdAt)}</td>
                  <td className="c-right mono strong">{fmtAmount(w.grossSourceAmount)}</td>
                  <td className="c-right mono">{fmtPercent(w.commissionPercent)}</td>
                  <td className="c-right mono strong">{fmtAmount(w.netSourceAmount)}</td>
                  <td className="c-right mono">{fmtRate(w.rate)}</td>
                  <td className="c-right mono strong">{fmtAmount(w.targetAmount)}</td>
                  <td className="c-id"><CopyValue value={w.address} head={6} tail={6} onCopy={copy} /></td>
                  <td className="c-act">
                    {w.status === 'NEW' && (
                        <span className="wd-actions">
                          <button
                              type="button" className="icon-btn icon-btn-approve"
                              title="Подтвердить заявку" aria-label="Подтвердить заявку"
                              onClick={() => setConfirm({ row: w, action: 'approve' })}
                          >
                            <i className="fa-solid fa-check" />
                          </button>
                          <button
                              type="button" className="icon-btn icon-btn-cancel"
                              title="Отменить заявку" aria-label="Отменить заявку"
                              onClick={() => setConfirm({ row: w, action: 'cancel' })}
                          >
                            <i className="fa-solid fa-xmark" />
                          </button>
                        </span>
                    )}
                  </td>
                </tr>
            ))}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Заявок не найдено</div>
          )}
          {loading && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего заявок"
        />

        {confirm && (
            <ConfirmWithdrawal
                row={confirm.row}
                action={confirm.action}
                busy={busy}
                onCancel={() => setConfirm(null)}
                onApply={() => process(confirm)}
            />
        )}
      </>
  );
}

/* ---- Дата и необязательное время двумя полями рядом ----
   Общий для всех фильтров кабинета: «Клиенты», «Ордера», «Заявки на вывод». */
function DateTimeField({ id, label, date, time, onDate, onTime }) {
  return (
      <div className="field">
        <label htmlFor={id}>
          {label} <span className="label-note">· время необязательно</span>
        </label>
        <div className="dt-row">
          <input id={id} type="date" value={date} onChange={(e) => onDate(e.target.value)} />
          <input
              type="time" step="1" value={time} aria-label={`${label}: время`}
              onChange={(e) => onTime(e.target.value)}
          />
        </div>
      </div>
  );
}

/* ---- Сокращённое значение: полное в подсказке, копирование по иконке ---- */
function CopyValue({ value, head, tail, onCopy }) {
  if (!value) return <span>—</span>;
  return (
      <span className="copyable" title={value} onClick={(e) => { e.stopPropagation(); onCopy(value); }}>
        <span className="mono">{shortValue(value, head, tail)}</span> <i className="fa-regular fa-copy" />
      </span>
  );
}

/* ---- Подтверждение действия над заявкой ----
   Текст и вид — по макету ТЗ. Адрес в окне сокращён, как на макете;
   полный адрес — во всплывающей подсказке. */
function ConfirmWithdrawal({ row, action, busy, onCancel, onApply }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  const approve = action === 'approve';
  const id = <span className="mono" title={row.id}>{shortValue(row.id)}</span>;
  const login = <b>{row.clientUsername || '—'}</b>;
  const sum = <b>{fmtAmount(row.grossSourceAmount)} ₽</b>;

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm">
          <div className="modal-head">
            <h2>{approve ? 'Подтвердить заявку?' : 'Отменить заявку?'}</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            {approve ? (
                <p className="confirm-text">
                  Заявка {id} клиента {login} на сумму {sum} будет подтверждена.
                  <br />
                  К отправке: <b>{fmtAmount(row.targetAmount)} USDT</b> на адрес{' '}
                  <span className="mono" title={row.address}>{shortValue(row.address, 6, 6)}</span>.
                </p>
            ) : (
                <p className="confirm-text">
                  Заявка {id} клиента {login} на сумму {sum} будет отменена.
                  <br />
                  Действие необратимо.
                </p>
            )}
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
              Отмена
            </button>
            <button
                type="button"
                className={`btn ${approve ? 'btn-primary' : 'btn-danger'}`}
                onClick={onApply}
                disabled={busy}
            >
              {approve ? 'Подтвердить' : 'Отменить заявку'}
            </button>
          </div>
        </div>
      </div>
  );
}