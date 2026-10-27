import {useCallback, useEffect, useRef, useState} from 'react';
import {api, dateTimeToMs, fmtDateTime, fmtMoney, fmtPercent, fmtRate, isUuid, logout, shortValue,} from './api.js';
// Общие для обоих кабинетов компоненты — frontend/shared.
import ConfirmDialog from '@shared/ConfirmDialog.jsx';
import {DocPage, DocsNav, docIdOf, NavGroup, sectionFromUrl, syncDocUrl} from '@shared/docs.jsx';
import ThemeToggle from '@shared/ThemeToggle.jsx';

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

// Ключ в localStorage: свёрнуто ли боковое меню на широком экране.
const SIDEBAR_KEY = 'support.sidebarCollapsed';

function readCollapsed() {
  try { return localStorage.getItem(SIDEBAR_KEY) === '1'; } catch { return false; }
}

// Порядок пунктов меню: «Заявки на вывод» — сразу под «Ордера»,
// «История мерчантов» — сразу над «КБ мерчантов».
const SECTIONS = [
  { id: 'clients', title: 'Клиенты', icon: 'fa-solid fa-users' },
  { id: 'orders', title: 'Ордера', icon: 'fa-solid fa-receipt' },
  { id: 'withdrawals', title: 'Заявки на вывод', icon: 'fa-solid fa-money-bill-transfer' },
  { id: 'transactions', title: 'Транзакции', icon: 'fa-solid fa-right-left' },
  {
    group: 'Мерчанты',
    icon: 'fa-solid fa-store',
    items: [
      { id: 'merchants', title: 'Конфигурация мерчантов', navTitle: 'Конфигурация', icon: 'fa-solid fa-sliders' },
      { id: 'merchantHistory', title: 'История мерчантов', navTitle: 'История', icon: 'fa-solid fa-clock-rotate-left' },
      { id: 'callbacks', title: 'КБ мерчантов', navTitle: 'КБ', icon: 'fa-solid fa-rotate' },
    ],
  },
  { id: 'incidents', title: 'Инциденты', icon: 'fa-solid fa-triangle-exclamation' },
];

/* Разделы только для администратора: оператор их не видит (ТЗ «Скрыть «Клиенты»
   и «Конфиги мерчантов» для операторов»). Роли — из GET /support-user/roles;
   ADMIN включает OPERATOR, поэтому признак «видит всё» — наличие ADMIN. */
const ADMIN_ONLY_SECTIONS = new Set(['clients', 'merchants']);

const SECTION_LIST = SECTIONS.flatMap((s) => s.items || [s]);

export default function App() {
  const [section, setSection] = useState(() => sectionFromUrl('orders'));
  // Меню на узком экране (≤720px) — выезжающая панель по кнопке ☰.
  const [menuOpen, setMenuOpen] = useState(false);
  // Меню на широком экране можно свернуть до полоски с иконками; выбор запоминается.
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [toast, setToast] = useState(null);
  // Страницы документации [{ id, title }]; пусто — пункта «Документация» в меню нет.
  const [docs, setDocs] = useState([]);
  const [orderStatuses, setOrderStatuses] = useState([]);
  // Справочник мерчантов: name -> displayName для показа человеку.
  const [merchants, setMerchants] = useState([]);
  // Способы оплаты: CARD -> «Карта» и так далее.
  const [methods, setMethods] = useState([]);
  // Статусы заявок на вывод: NEW -> «Новая» и так далее.
  const [withdrawalStatuses, setWithdrawalStatuses] = useState([]);
  // Операции (CREDIT/DEBIT) и типы транзакций — для раздела «Транзакции».
  const [operations, setOperations] = useState([]);
  const [txTypes, setTxTypes] = useState([]);
  // Статусы и типы инцидентов — для раздела «Инциденты».
  // У типа: summary — в таблицу, description — описание, guide — «Что необходимо сделать».
  const [incidentStatuses, setIncidentStatuses] = useState([]);
  const [incidentTypes, setIncidentTypes] = useState([]);
  // Клиент, выбранный переходом из таблицы в конфигурацию мерчантов.
  const [configClient, setConfigClient] = useState(null);
  // Роли вошедшего пользователя; null — ещё загружаются.
  // Пока роли неизвестны, разделы администратора не показываем.
  const [roles, setRoles] = useState(null);
  const isAdmin = Array.isArray(roles) && roles.includes('ADMIN');
  const canSee = (id) => isAdmin || !ADMIN_ONLY_SECTIONS.has(id);
  const visibleSections = SECTIONS
      .map((s) => (s.items ? { ...s, items: s.items.filter((i) => canSee(i.id)) } : s))
      .filter((s) => (s.items ? s.items.length > 0 : canSee(s.id)));

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type, key: Date.now() });
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

  useEffect(() => {
    api.supportUserRoles()
        .then(setRoles)
        .catch((e) => {
          // Без ролей остаются только общие разделы — как у оператора.
          setRoles([]);
          showToast(e.message || 'Не удалось загрузить роли пользователя', 'error');
        });
  }, [showToast]);

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
          if (Array.isArray(d?.Operation) && d.Operation.length) setOperations(d.Operation);
          if (Array.isArray(d?.TransactionType) && d.TransactionType.length) setTxTypes(d.TransactionType);
          if (Array.isArray(d?.IncidentStatus) && d.IncidentStatus.length) setIncidentStatuses(d.IncidentStatus);
          if (Array.isArray(d?.IncidentType) && d.IncidentType.length) setIncidentTypes(d.IncidentType);
        })
        .catch(() => { /* без словаря покажутся коды значений */ });
  }, []);

  const openMerchantConfig = (client) => {
    setConfigClient(client);
    setSection('merchants');
  };

  // Документация: список страниц — один раз при открытии кабинета.
  // Ошибку не показываем: без списка пункта «Документация» просто нет.
  useEffect(() => {
    let alive = true;
    api.docs().then((list) => { if (alive) setDocs(list); }).catch(() => {});
    return () => { alive = false; };
  }, []);

  // Escape закрывает выехавшее меню.
  useEffect(() => {
    if (!menuOpen) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') setMenuOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  const toggleCollapsed = () => {
    setCollapsed((v) => {
      const next = !v;
      try { localStorage.setItem(SIDEBAR_KEY, next ? '1' : '0'); } catch { /* без запоминания */ }
      return next;
    });
  };

  const goTo = (id) => { setSection(id); setMenuOpen(false); };

  useEffect(() => { syncDocUrl(section); }, [section]);

  const docId = docIdOf(section);
  const current = docId
      ? { id: section, title: docs.find((d) => d.id === docId)?.title || 'Документация' }
      : SECTION_LIST.find((s) => s.id === section);

  return (
      <div className="layout">
        <aside
            className={`sidebar${menuOpen ? ' open' : ''}${collapsed ? ' collapsed' : ''}`}
            aria-label="Разделы"
        >
          <div className="brand">
            <span className="brand-ico"><i className="fa-solid fa-shield-halved" /></span>
            <span className="brand-name">Кабинет API</span>
            <button
                type="button"
                className="menu-close"
                aria-label="Закрыть меню"
                onClick={() => setMenuOpen(false)}
            >
              <i className="fa-solid fa-xmark" />
            </button>
          </div>
          <nav className="nav">
            {visibleSections.map((s) => (s.items ? (
                <NavGroup
                    key={s.group}
                    title={s.group}
                    icon={s.icon}
                    active={s.items.some((i) => i.id === section)}
                    collapsed={collapsed}
                >
                  {s.items.map((i) => (
                      <button
                          key={i.id}
                          type="button"
                          className={`nav-item nav-sub-item${section === i.id ? ' active' : ''}`}
                          onClick={() => goTo(i.id)}
                          title={collapsed ? i.title : undefined}
                      >
                        <i className={i.icon} />
                        <span>{i.navTitle || i.title}</span>
                      </button>
                  ))}
                </NavGroup>
            ) : (
                <button
                    key={s.id}
                    type="button"
                    className={`nav-item${section === s.id ? ' active' : ''}`}
                    onClick={() => goTo(s.id)}
                    title={collapsed ? s.title : undefined}
                >
                  <i className={s.icon} />
                  <span>{s.title}</span>
                </button>
            )))}
            <DocsNav docs={docs} section={section} collapsed={collapsed} onOpen={goTo} />
          </nav>
          {/* Свернуть/развернуть меню — только на широком экране. */}
          <button
              type="button"
              className="sidebar-toggle"
              onClick={toggleCollapsed}
              aria-label={collapsed ? 'Развернуть меню' : 'Свернуть меню'}
              title={collapsed ? 'Развернуть меню' : 'Свернуть меню'}
          >
            <i className={`fa-solid ${collapsed ? 'fa-angles-right' : 'fa-angles-left'}`} />
            <span>Свернуть</span>
          </button>
        </aside>
        {/* Затемнение под выехавшим меню: клик по нему закрывает меню. */}
        {menuOpen && <div className="menu-backdrop" onClick={() => setMenuOpen(false)} />}

        <div className="main">
          <header className="topbar">
            <div className="topbar-title">
              <button
                  type="button"
                  className="menu-btn"
                  aria-label="Открыть меню"
                  aria-expanded={menuOpen}
                  onClick={() => setMenuOpen(true)}
              >
                <i className="fa-solid fa-bars" />
              </button>
              <h1>{current.title}</h1>
            </div>
            <div className="topbar-right">
              <ThemeToggle />
              <AccountMenu />
            </div>
          </header>
          <main className="content">
            {section === 'clients' && canSee('clients') && (
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
                    canOpenClient={isAdmin}
                />
            )}
            {section === 'merchants' && canSee('merchants') && (
                <MerchantsSection
                    client={configClient}
                    showToast={showToast}
                    onPickClient={setConfigClient}
                />
            )}
            {section === 'merchantHistory' && (
                <MerchantHistorySection merchants={merchants} methods={methods} showToast={showToast} />
            )}
            {section === 'transactions' && (
                <TransactionsSection
                    operations={operations}
                    txTypes={txTypes}
                    showToast={showToast}
                />
            )}
            {section === 'withdrawals' && (
                <WithdrawalsSection
                    withdrawalStatuses={withdrawalStatuses}
                    showToast={showToast}
                />
            )}
            {section === 'callbacks' && (
                <CallbacksSection merchants={merchants} showToast={showToast} />
            )}
            {section === 'incidents' && (
                <IncidentsSection
                    incidentStatuses={incidentStatuses}
                    incidentTypes={incidentTypes}
                    orderStatuses={orderStatuses}
                    merchants={merchants}
                    methods={methods}
                    showToast={showToast}
                    canOpenClient={isAdmin}
                />
            )}
            {docId && <DocPage key={docId} id={docId} load={api.doc} />}
          </main>
        </div>

        {toast && <div className={`toast toast-${toast.type}`}>{toast.message}</div>}
      </div>
  );
}

/* ==================== Сортировка по клику на заголовок ====================
   Сортирует бэк (параметр sort=поле,asc|desc), поэтому порядок общий для всей
   выборки с учётом фильтра, а не только для текущей страницы.
   Клик по колонке: по возрастанию → по убыванию → исходный порядок.
   Имена полей проверены живыми запросами: неизвестное поле бэк отдаёт 500. */

function nextSort(sort, field) {
  if (!sort || sort.field !== field) return { field, dir: 'asc' };
  if (sort.dir === 'asc') return { field, dir: 'desc' };
  return null;
}

function sortParam(sort) {
  return sort ? `${sort.field},${sort.dir}` : undefined;
}

function SortTh({ field, sort, onSort, className = '', children }) {
  const active = sort?.field === field;
  const icon = !active ? 'fa-sort' : sort.dir === 'asc' ? 'fa-arrow-up-long' : 'fa-arrow-down-long';
  return (
      <th
          className={`${className} th-sortable${active ? ' active' : ''}`.trim()}
          aria-sort={!active ? 'none' : sort.dir === 'asc' ? 'ascending' : 'descending'}
      >
        <button type="button" className="th-sort" onClick={() => onSort(field)} title="Сортировать">
          {children}
          <i className={`fa-solid ${icon} sort-ico`} aria-hidden="true" />
        </button>
      </th>
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
  const [sort, setSort] = useState(null); // null | { field, dir }

  const idInvalid = draft.id.trim() !== '' && !isUuid(draft.id);

  const load = useCallback(async (f, p, srt) => {
    setLoading(true);
    try {
      const params = { page: p, size: PAGE_SIZE };
      if (srt) params.sort = sortParam(srt);
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

  useEffect(() => { load(applied, page, sort); }, [load, applied, page, sort]);

  const setF = (k, v) => setDraft((p) => ({ ...p, [k]: v }));

  const search = () => {
    if (idInvalid) return;
    setApplied(draft);
    setPage(0);
  };
  const reset = () => {
    setDraft(EMPTY_FILTER);
    setApplied({ ...EMPTY_FILTER });
    setSort(null);
    setPage(0);
  };
  // Новая сортировка — с первой страницы.
  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };

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

  // Клиент удалён: карточка закрывается, список перечитывается (ТЗ «Удаление клиента»).
  // Удалили последнего на странице — переходим на предыдущую, чтобы не остаться на пустой.
  const onDeleted = () => {
    setCard(null);
    showToast('Клиент удалён', 'success');
    if (rows.length === 1 && page > 0) setPage(page - 1);
    else load(applied, page, sort);
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
            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="f-mode">Дата регистрации</label>
                <select id="f-mode" value={draft.dateMode} onChange={(e) => setF('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>
              <div className="date-fields">
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
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={idInvalid}>
              <i className="fa-solid fa-magnifying-glass" /> Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset}>Сбросить</button>
            <RefreshButton onClick={() => load(applied, page, sort)} loading={loading} />
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
                    <SortTh field="id" sort={sort} onSort={onSort}>ID</SortTh>
                    <SortTh field="username" sort={sort} onSort={onSort}>Логин</SortTh>
                    <SortTh field="registeredAt" sort={sort} onSort={onSort}>Дата регистрации</SortTh>
                    <SortTh field="commissionPercent" sort={sort} onSort={onSort}>Комиссия</SortTh>
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
                        <td className="mono">{fmtPercent(c.commissionPercent)}</td>
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
                onDeleted={onDeleted}
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


/* ==================== Документация ====================
   Пункт меню, страница и адрес ?doc={id} — общие для обоих кабинетов:
   frontend/shared/docs.jsx. */

/* ==================== Меню аккаунта ==================== */
/* Логин администратора в правом углу верхней панели.
   Клик раскрывает меню с выходом; повторный клик или клик вне меню закрывает.

   ВНИМАНИЕ: эндпоинта «текущий пользователь» в коллекции нет, поэтому
   логин пока не запрашивается — показывается общая подпись. Как только
   бэк отдаст его (например, GET /api/support/support-user/current),
   достаточно подставить значение в состояние name. */
function AccountMenu() {
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [open, setOpen] = useState(false);
  // Логин вошедшего администратора — из GET /support-user.
  // Пока ответа нет или запрос не удался — нейтральное «Администратор».
  const [name, setName] = useState('Администратор');
  const ref = useRef(null);

  useEffect(() => {
    let alive = true;
    api.supportUser()
        .then((u) => { if (alive && u?.username) setName(u.username); })
        .catch(() => { /* оставляем «Администратор» */ });
    return () => { alive = false; };
  }, []);

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
              {/* Управление аккаунтом (страница Keycloak) — в новой вкладке */}
              <a
                  className="account-item"
                  href="/manage-account"
                  target="_blank"
                  rel="noopener noreferrer"
                  role="menuitem"
                  onClick={() => setOpen(false)}
              >
                <i className="fa-solid fa-user-gear" />
                Управление аккаунтом
              </a>
              {/* Выход — сначала окно подтверждения, затем POST /logout (см. logout в api.js); href — запасной вариант */}
              <a
                  className="account-item is-danger"
                  href="/logout"
                  role="menuitem"
                  onClick={(e) => { e.preventDefault(); setOpen(false); setConfirmLogout(true); }}
              >
                <i className="fa-solid fa-right-from-bracket" />
                Выйти
              </a>
            </div>
        )}

        {confirmLogout && <LogoutDialog onCancel={() => setConfirmLogout(false)} />}
      </div>
  );
}

/* Подтверждение выхода. Нужно, потому что выход идёт без стандартной
   страницы подтверждения Spring (см. logout в api.js). */
function LogoutDialog({ onCancel }) {
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  const apply = () => { setBusy(true); logout(); }; // дальше страница уходит на выход сама

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm">
          <div className="modal-head">
            <h2>Выход из аккаунта</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="confirm-text">Выйти из личного кабинета?</p>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Отмена</button>
            <button type="button" className="btn btn-danger" onClick={apply} disabled={busy}>
              {busy ? 'Выходим…' : 'Выйти'}
            </button>
          </div>
        </div>
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

function OrdersSection({ orderStatuses, merchants, methods, showToast, canOpenClient }) {
  const [draft, setDraft] = useState(EMPTY_ORDER_FILTER);
  const [applied, setApplied] = useState(EMPTY_ORDER_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [card, setCard] = useState(null);
  const [sort, setSort] = useState(null); // null | { field, dir }

  const load = useCallback(async (filter, pageNum, srt) => {
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
        sort: sortParam(srt),
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

  useEffect(() => { load(applied, page, sort); }, [applied, page, sort, load]);

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const search = () => { setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setSort(null); setDraft(EMPTY_ORDER_FILTER); setApplied(EMPTY_ORDER_FILTER); };
  // Новая сортировка — с первой страницы.
  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };
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
            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="o-mode">Дата создания</label>
                <select id="o-mode" value={draft.dateMode}
                        onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>
              <div className="date-fields">
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
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={loading}>
              <i className="fa-solid fa-magnifying-glass" />
              Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset} disabled={loading}>
              Сбросить
            </button>
            <RefreshButton onClick={() => load(applied, page, sort)} loading={loading} />
          </div>
        </div>

        <p className="table-hint">Двойной клик по строке — карточка ордера и управление статусом</p>

        <div className="table-wrap">
          <table className="grid grid-orders">
            <thead>
            <tr>
              <SortTh field="id" sort={sort} onSort={onSort}>ID ордера</SortTh>
              <SortTh field="internalId" sort={sort} onSort={onSort}>ID в системе клиента</SortTh>
              <SortTh field="clientUsername" sort={sort} onSort={onSort}>Клиент</SortTh>
              <SortTh field="amount" sort={sort} onSort={onSort} className="c-right">Сумма, руб.</SortTh>
              <SortTh field="status" sort={sort} onSort={onSort}>Статус</SortTh>
              <SortTh field="createdAt" sort={sort} onSort={onSort}>Создан</SortTh>
              <SortTh field="merchant" sort={sort} onSort={onSort}>Мерчант</SortTh>
              <SortTh field="merchantOrderId" sort={sort} onSort={onSort}>ID у мерчанта</SortTh>
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
                    <span className="cell-sub"><CopyValue value={o.clientId} onCopy={copy} /></span>
                  </td>
                  <td className="c-right mono">{fmtAmount(o.amount)}</td>
                  <td>
                  <span className={`badge badge-${orderStatusMod(o.status)}`}>
                    {statusLabel(o.status)}
                  </span>
                  </td>
                  <td className="mono">{fmtDateTime(o.createdAt)}</td>
                  <td>{merchantLabel(o.merchant)}</td>
                  {/* ID у мерчанта — сокращённо, полный в подсказке, копируется по клику */}
                  <td className="c-id"><CopyValue value={o.merchantOrderId} onCopy={copy} /></td>
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
                onStatusChanged={() => load(applied, page, sort)}
                canOpenClient={canOpenClient}
            />
        )}
      </>
  );
}

/* ==================== Карточка ордера ==================== */
/* Ключевые значения — сумма и статус — вынесены отдельной строкой,
   остальное в двух колонках. Над кнопками — полоса «Управление статусом»:
   ручная смена статуса (PATCH /order/{id}), см. ORDER_ACTIONS. */

function OrderCard({ order, orderStatuses, merchants, methods, onClose, onCopy, showToast, onStatusChanged, canOpenClient }) {
  const [client, setClient] = useState(null);   // карточка клиента поверх
  const [loadingClient, setLoadingClient] = useState(false);
  /* В списке приходит сокращённый набор полей, поэтому подробности
     (метод, банк, реквизиты, срок, статус у мерчанта) дозапрашиваем.
     До ответа показываем то, что уже есть из строки таблицы. */
  const [full, setFull] = useState(order);
  const [loading, setLoading] = useState(true);
  // Перечитать ордер после смены статуса.
  const [reloadKey, setReloadKey] = useState(0);
  // Действие в окне подтверждения (элемент ORDER_ACTIONS) и идёт ли запрос.
  const [confirmAction, setConfirmAction] = useState(null);
  const [changing, setChanging] = useState(false);
  const [sendingCallback, setSendingCallback] = useState(false);

  /* «Отправить КБ» (ТЗ «Кнопка „Отправить КБ“ в карточке ордера»): повторная
     отправка клиенту последнего недоставленного КБ по ордеру.
     400 — текст ошибки из ответа (например, КБ уже доставлен),
     остальные ошибки — общий текст. */
  const sendCallback = async () => {
    setSendingCallback(true);
    try {
      await api.resendClientCallback(full.id);
      showToast('Колбэк отправлен', 'success');
    } catch (e) {
      showToast(e.status === 400 && e.message ? e.message : 'Не удалось отправить колбэк', 'error');
    } finally {
      setSendingCallback(false);
    }
  };

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api.order(order.id)
        .then((d) => { if (alive && d) setFull((prev) => ({ ...prev, ...d })); })
        .catch((e) => {
          if (alive) showToast(e.message || 'Не удалось загрузить ордер', 'error');
        })
        .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [order, reloadKey, showToast]);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !client && !confirmAction) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, client, confirmAction]);

  // Кнопки — только для разрешённых переходов из текущего статуса.
  // Пока подробности не загружены, статус может быть устаревшим — кнопок не показываем.
  const actions = loading ? [] : ORDER_ACTIONS.filter((a) => a.from.includes(full.status));
  const statusName = (name) => orderStatuses.find((s) => s.name === name)?.description || name || '—';

  const applyStatus = async () => {
    const action = confirmAction;
    setChanging(true);
    try {
      await api.setOrderStatus(full.id, action.status);
      showToast('Статус ордера изменён', 'success');
    } catch (e) {
      // Например, статус успели поменять, пока карточка была открыта, — бэк отвечает 400 с текстом.
      showToast(e.message || 'Не удалось изменить статус', 'error');
    } finally {
      setChanging(false);
      setConfirmAction(null);
      // В любом случае перечитываем карточку и таблицу: статус на сервере мог измениться.
      setReloadKey((k) => k + 1);
      onStatusChanged?.();
    }
  };

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

          {/* Управление статусом: подпись слева, кнопки разрешённых переходов справа.
              Если переходов нет (например, ордер «Успешный») — полосы нет. */}
          {actions.length > 0 && (
              <div className="status-bar">
                <span className="eyebrow">Управление статусом</span>
                <div className="status-actions">
                  {actions.map((a) => (
                      <button
                          key={a.status}
                          type="button"
                          className={`btn btn-sm ${a.btn}`}
                          onClick={() => setConfirmAction(a)}
                          disabled={changing}
                      >
                        <i className={a.icon} />
                        {a.label}
                      </button>
                  ))}
                </div>
              </div>
          )}

          {/* «Открыть карточку клиента» ведёт в раздел «Клиенты» — оператору не показываем.
              «Отправить КБ» — в том же ряду, видна всем. */}
          <div className="modal-foot modal-foot-split">
            <div className="foot-left">
              {canOpenClient && (
                  <button type="button" className="btn btn-secondary" onClick={openClient} disabled={loadingClient}>
                    <i className="fa-solid fa-user" />
                    Открыть карточку клиента
                  </button>
              )}
              <button type="button" className="btn btn-outline" onClick={sendCallback} disabled={sendingCallback}>
                <i className={sendingCallback ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-paper-plane'} />
                Отправить КБ
              </button>
            </div>
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

        {confirmAction && (
            <ConfirmOrderStatus
                orderId={full.id}
                action={confirmAction}
                from={statusName(full.status)}
                to={statusName(confirmAction.status)}
                busy={changing}
                onCancel={() => setConfirmAction(null)}
                onApply={applyStatus}
            />
        )}
      </div>
  );
}

/* ---- Ручная смена статуса ордера ----
   Из каких статусов доступен переход (ТЗ «Управление статусом ордера»):
   в SUCCESS — из любого, кроме самого SUCCESS; в CANCELED — только из NEW
   и DISPUTE; в DISPUTE — из NEW и TIMEOUT. NEW и TIMEOUT вручную не ставятся.
   Исключение (ТЗ «Статусы „Поиск реквизитов“ и „Реквизиты не найдены“»):
   из SEARCHING — только CANCELED, из DETAILS_NOT_FOUND — ничего.
   Окончательно переход проверяет бэк. */
const ORDER_ACTIONS = [
  {
    status: 'SUCCESS', label: 'Подтвердить', question: 'Подтвердить ордер?',
    from: ['NEW', 'TIMEOUT', 'DISPUTE', 'CANCELED'],
    btn: 'btn-success', icon: 'fa-solid fa-check',
  },
  {
    status: 'CANCELED', label: 'Отклонить', question: 'Отклонить ордер?',
    // SEARCHING — «Поиск реквизитов» (ТЗ «Статусы „Поиск реквизитов“ и „Реквизиты не найдены“»):
    // из него доступна только отмена. Из DETAILS_NOT_FOUND переходов нет — полоса не выводится.
    from: ['NEW', 'DISPUTE', 'SEARCHING'],
    btn: 'btn-danger', icon: 'fa-solid fa-xmark',
  },
  {
    status: 'DISPUTE', label: 'Спор', question: 'Перевести ордер в спор?',
    from: ['NEW', 'TIMEOUT'],
    btn: 'btn-dispute', icon: 'fa-solid fa-scale-balanced',
  },
];

/* Подтверждение смены статуса: ID ордера, вопрос, «с … на …». */
function ConfirmOrderStatus({ orderId, action, from, to, busy, onCancel, onApply }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm" role="dialog" aria-modal="true" aria-labelledby="os-title">
          <div className="modal-head">
            <h2 id="os-title">Подтверждение</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="confirm-id mono">{orderId}</p>
            <p className="confirm-text"><b>{action.question}</b></p>
            <p className="confirm-text">
              Статус изменится с «{from}» на «{to}».
              <br />
              Действие необратимо.
            </p>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Отмена</button>
            <button type="button" className={`btn ${action.btn}`} onClick={onApply} disabled={busy}>
              {busy ? <i className="fa-solid fa-spinner fa-spin" /> : <i className={action.icon} />}
              {action.label}
            </button>
          </div>
        </div>
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

/* Цвет бейджа статуса ордера. Успешный — зелёный, отменённый и
   «Реквизиты не найдены» — красный, просроченный и спорный — жёлтый,
   «Поиск реквизитов» — бирюзовый (как в документации), новый — синий. */
function orderStatusMod(name) {
  if (name === 'SUCCESS') return 'ok';
  if (name === 'CANCELED' || name === 'DETAILS_NOT_FOUND') return 'bad';
  if (name === 'SEARCHING') return 'cyan';
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
   окно тоже закрывается.

   «Удалить клиента» — слева в подвале, только когда карточка открыта
   из раздела «Клиенты» (передан onDeleted). Из карточек ордера и
   инцидента клиента не удаляем: они остались бы открытыми на удалённом. */

// Предел длины комментария к клиенту (ТЗ).
const COMMENT_MAX = 1000;

function ClientCard({ client, methods, onClose, onUpdated, onDeleted, onCopy, showToast }) {
  // Черновик настроек. Строки, а не числа: поле ввода всегда работает
  // со строкой, приведение и проверка — при сохранении.
  const initial = () => ({
    commission: client.commissionPercent ?? '',
    timeout: client.orderTimeoutSeconds ?? '',
    minWithdrawal: client.minWithdrawalAmount ?? '',
    comment: client.comment ?? '',
    methods: Array.isArray(client.methods) ? client.methods : [],
    ipRestriction: Boolean(client.ipRestrictionEnabled),
  });

  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [recalculating, setRecalculating] = useState(false);
  // Открыто окно «Пересчитать баланс?» — запрос уходит только после «Да».
  const [confirmRecalc, setConfirmRecalc] = useState(false);
  // Открыто окно удаления клиента.
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Перерасчёт баланса по сумме транзакций клиента (ТЗ «Инциденты»).
  // Бэк возвращает клиента после пересчёта — обновляем карточку и таблицу;
  // несохранённые правки в полях настроек при этом не теряются.
  const onRecalculate = async () => {
    setRecalculating(true);
    try {
      const updated = await api.recalculateBalance(client.id);
      if (updated && typeof updated === 'object') onUpdated({ ...client, ...updated });
      showToast('Баланс пересчитан', 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось пересчитать баланс', 'error');
    } finally {
      setRecalculating(false);
      setConfirmRecalc(false);
    }
  };

  // Клиент мог обновиться снаружи (например, после сохранения) —
  // подхватываем новые значения.
  const methodsKey = (client.methods || []).join(',');
  useEffect(() => { setForm(initial()); setErrors({}); },
      // eslint-disable-next-line react-hooks/exhaustive-deps
      [client.id, client.commissionPercent, client.orderTimeoutSeconds,
        client.minWithdrawalAmount, client.comment, client.ipRestrictionEnabled, methodsKey]);

  // Раскрыт ли список методов: Escape тогда закрывает только его.
  const [methodsOpen, setMethodsOpen] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !methodsOpen && !confirmRecalc && !confirmDelete) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, methodsOpen, confirmRecalc, confirmDelete]);

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

    // Минимальная сумма вывода: целое число рублей от 0 (по ТЗ).
    // Бэк обновляет только не-null поля, поэтому очистить уже заданное
    // значение нельзя — пустое поле допустимо, только если суммы и не было.
    const rawMin = String(form.minWithdrawal).trim();
    const hadMin = client.minWithdrawalAmount != null && client.minWithdrawalAmount !== '';
    if (rawMin === '') {
      if (hadMin) errs.minWithdrawal = 'Укажите целое число не меньше нуля';
    } else if (!/^\d+$/.test(rawMin)) {
      errs.minWithdrawal = 'Укажите целое число не меньше нуля';
    } else if (Number(rawMin) !== Number(client.minWithdrawalAmount)) {
      body.minWithdrawalAmount = Number(rawMin);
    }

    // Комментарий: до 1000 символов (длинее ввести не даёт поле), пустой допустим.
    // Пустая строка — не null, поэтому бэк её сохранит и комментарий очистится.
    if (form.comment !== (client.comment ?? '')) body.comment = form.comment;

    // Способы оплаты: шлём весь выбранный набор, только если он изменился.
    // Пустой массив отвязывает все методы, отсутствие поля — ничего не меняет.
    const was = [...(client.methods || [])].sort().join(',');
    const now = [...form.methods].sort().join(',');
    if (was !== now) body.methods = form.methods;

    // Ограничение белого списка IP: шлём, только если переключатель изменили.
    if (form.ipRestriction !== Boolean(client.ipRestrictionEnabled)) body.ipRestrictionEnabled = form.ipRestriction;

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

                <FieldRow
                    label="Минимальная сумма вывода"
                    error={errors.minWithdrawal}
                    hint="Сумма указывается до применения комиссии"
                >
                <span className="input-suffix">
                  <input
                      type="text" inputMode="numeric" placeholder="0"
                      value={form.minWithdrawal}
                      disabled={saving}
                      className={errors.minWithdrawal ? 'invalid' : ''}
                      onChange={(e) => setField('minWithdrawal', e.target.value)}
                  />
                  <span className="suffix">₽</span>
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

            {/* ---------- Ограничение белого списка IP: под блоками, над комментарием ----------
                 Включено — запросы клиента к API принимаются только с адресов
                 из его белого списка (список ведёт сам клиент в своём кабинете). */}
            <div className="client-switch">
              <span className="eyebrow" id="ip-restriction-label">Ограничение белого списка IP адресов</span>
              <label className="switch-row">
                <span className="switch">
                  <input
                      type="checkbox"
                      aria-labelledby="ip-restriction-label"
                      checked={form.ipRestriction}
                      disabled={saving}
                      onChange={(e) => setField('ipRestriction', e.target.checked)}
                  />
                  <span className="track" />
                  <span className="thumb" />
                </span>
                <span>{form.ipRestriction ? 'Включено' : 'Выключено'}</span>
              </label>
            </div>

            {/* ---------- Комментарий: во всю ширину под блоками ---------- */}
            <div className="client-comment">
              <label className="eyebrow" htmlFor="client-comment">Комментарий</label>
              <textarea
                  id="client-comment"
                  rows={3}
                  maxLength={COMMENT_MAX}
                  placeholder="Заметка администратора о клиенте"
                  value={form.comment}
                  disabled={saving}
                  onChange={(e) => setField('comment', e.target.value.slice(0, COMMENT_MAX))}
              />
              <span className={`char-count${form.comment.length >= COMMENT_MAX ? ' is-full' : ''}`}>
                {form.comment.length} / {COMMENT_MAX}
              </span>
            </div>
          </div>

          <div className="modal-foot modal-foot-split">
            {onDeleted && (
                <button type="button" className="btn btn-danger-soft" disabled={saving || recalculating} onClick={() => setConfirmDelete(true)}>
                  <i className="fa-solid fa-trash" />
                  Удалить клиента
                </button>
            )}
            <div className="foot-right">
              <button type="button" className="btn btn-secondary" disabled={saving || recalculating} onClick={() => setConfirmRecalc(true)}>
                <i className={`fa-solid fa-rotate${recalculating ? ' fa-spin' : ''}`} />
                Перерасчёт баланса
              </button>
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

        {confirmRecalc && (
            <ConfirmDialog
                title="Перерасчёт баланса"
                busy={recalculating}
                onConfirm={onRecalculate}
                onCancel={() => setConfirmRecalc(false)}
            >
              Пересчитать баланс клиента <b>{client.username || client.id}</b> по сумме его транзакций?
            </ConfirmDialog>
        )}
        {confirmDelete && (
            <DeleteClientDialog
                client={client}
                showToast={showToast}
                onCancel={() => setConfirmDelete(false)}
                onDeleted={() => { setConfirmDelete(false); onDeleted(); }}
            />
        )}
      </div>
  );
}

/* ---- Окно удаления клиента ----
   Логин и ID удаляемого клиента, поле для слова «удалить». Кнопка
   «Удалить» активна, только когда введено ровно это слово: регистр
   не учитывается, пробелы по краям отбрасываются. Удаляется всё,
   что связано с клиентом (ордера, заявки, транзакции, токены), поэтому
   подтверждение — вводом слова, а не одной кнопкой. */
const DELETE_WORD = 'удалить';

function DeleteClientDialog({ client, showToast, onCancel, onDeleted }) {
  const [word, setWord] = useState('');
  const [busy, setBusy] = useState(false);
  const ready = word.trim().toLowerCase() === DELETE_WORD;

  useEffect(() => {
    const onKey = (e) => {
      if (e.key !== 'Escape' || busy) return;
      // Escape закрывает только это окно, а не карточку клиента под ним.
      e.stopImmediatePropagation();
      onCancel();
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onCancel, busy]);

  const apply = async () => {
    if (!ready || busy) return;
    setBusy(true);
    try {
      await api.deleteClient(client.id);
      onDeleted();
    } catch (e) {
      showToast(e.message || 'Не удалось удалить клиента', 'error');
      setBusy(false);
    }
  };

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm" role="dialog" aria-modal="true" aria-labelledby="dc-title">
          <div className="ocard-head">
            <span className="ocard-ico ocard-ico-danger"><i className="fa-solid fa-trash" /></span>
            <div className="ocard-title">
              <span className="ocard-name" id="dc-title">Удаление клиента</span>
              <span className="ocard-sub">{client.username || '—'}</span>
            </div>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="danger-box">
              Клиент <b>{client.username || '—'}</b> · <b className="mono">{client.id}</b> будет удалён.
              Действие необратимо.
            </p>
            <label className="eyebrow inc-label" htmlFor="dc-word">
              Чтобы подтвердить, введите слово <b className="delete-word">{DELETE_WORD}</b>
            </label>
            <input
                id="dc-word"
                className="dc-input"
                type="text"
                autoComplete="off"
                placeholder={DELETE_WORD}
                value={word}
                disabled={busy}
                autoFocus
                onChange={(e) => setWord(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') apply(); }}
            />
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Отмена</button>
            <button type="button" className="btn btn-danger" onClick={apply} disabled={!ready || busy}>
              {busy && <i className="fa-solid fa-spinner fa-spin" />}
              Удалить
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
// hint — подсказка под полем; при ошибке вместо неё показывается текст ошибки.
function FieldRow({ label, error, hint, children }) {
  return (
      <div className={`fitem fitem-field${error ? ' has-error' : ''}`}>
        <span className="eyebrow">{label}</span>
        <span className="fvalue">{children}</span>
        {error && <span className="field-error">{error}</span>}
        {!error && hint && <span className="field-hint">{hint}</span>}
      </div>
  );
}

/* ==================== Конфигурация мерчантов ==================== */
/* Очерёдность: список всегда идёт по merchantOrder (у кого раньше
   запрашиваются реквизиты). Порядок меняется перетаскиванием строки за
   «ручку» или кнопками вверх/вниз; номер не показываем (решение бэка).
   На одну перестановку — один PATCH { merchantOrder }: соседей бэк
   сдвигает сам, после ответа список перечитываем.
   Пока применён поиск, порядок менять нельзя — среди скрытых строк
   непонятно, куда вставлять.
   Страниц и сортировки по колонкам нет намеренно: иначе нельзя
   перетаскивать между страницами (договорились с бэком). */

// Мерчанты по очерёдности; без номера — в конец, в порядке от бэка.
function byMerchantOrder(list) {
  return list
      .map((r, i) => ({ r, i }))
      .sort((a, b) => {
        const ao = a.r.merchantOrder ?? Infinity;
        const bo = b.r.merchantOrder ?? Infinity;
        return ao === bo ? a.i - b.i : ao - bo;
      })
      .map((x) => x.r);
}

function MerchantsSection({ client, showToast, onPickClient }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');   // что набрано в поле
  const [applied, setApplied] = useState(''); // что применено кнопкой
  const [moving, setMoving] = useState(false);  // идёт сохранение очерёдности
  const [armedId, setArmedId] = useState(null); // строка, взятая за «ручку»
  const [dragId, setDragId] = useState(null);   // строка, которую тащим
  const [drop, setDrop] = useState(null);       // { id, after } — куда вставим

  // silent — перечитать без спиннера (после перестановки).
  const load = useCallback(async (clientId, silent = false) => {
    if (!silent) setLoading(true);
    try {
      const d = await api.merchantConfigs(clientId);
      setRows(byMerchantOrder(Array.isArray(d) ? d : []));
    } catch (e) {
      showToast(e.message || 'Не удалось загрузить конфигурацию', 'error');
      if (!silent) setRows([]);
    } finally {
      if (!silent) setLoading(false);
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

  // Перестановка строки с позиции from на позицию to (индексы в rows).
  // Новый merchantOrder — номер, который сейчас занимает позиция to:
  // так работает и при номерах не подряд. Строку двигаем сразу, при
  // ошибке возвращаем как было.
  const move = async (from, to) => {
    if (moving || from === to || to < 0 || to >= rows.length) return;
    const prev = rows;
    const row = rows[from];
    const merchantOrder = rows[to].merchantOrder ?? to + 1;
    const next = [...rows];
    next.splice(from, 1);
    next.splice(to, 0, row);
    setRows(next);
    setMoving(true);
    try {
      await api.updateMerchantConfig(row.id, { merchantOrder });
      await load(client.id, true);
    } catch (e) {
      setRows(prev);
      showToast(e.message || 'Не удалось изменить очерёдность', 'error');
    } finally {
      setMoving(false);
    }
  };

  // ---- Перетаскивание (HTML5 drag-and-drop) ----
  // Строка становится перетаскиваемой только пока зажата «ручка» —
  // иначе мешало бы выделять текст и править суммы в ячейках.
  const endDrag = () => { setArmedId(null); setDragId(null); setDrop(null); };

  const onDragOver = (e, r) => {
    if (dragId == null) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    const box = e.currentTarget.getBoundingClientRect();
    const after = e.clientY > box.top + box.height / 2;
    if (!drop || drop.id !== r.id || drop.after !== after) setDrop({ id: r.id, after });
  };

  const onDrop = (e) => {
    e.preventDefault();
    if (dragId == null || !drop) { endDrag(); return; }
    const from = rows.findIndex((x) => x.id === dragId);
    let to = rows.findIndex((x) => x.id === drop.id) + (drop.after ? 1 : 0);
    if (from < to) to -= 1; // строка ушла со своего места — индексы сдвинулись
    endDrag();
    move(from, to);
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
  const canReorder = !applied && rows.length > 1;

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
            <RefreshButton onClick={() => client?.id && load(client.id)} loading={loading} />
          </div>
        </div>

        {loading ? (
            <div className="state"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
        ) : list.length === 0 ? (
            <div className="state"><i className="fa-solid fa-store-slash" /> Мерчанты не найдены</div>
        ) : (
            <>
              <p className="table-hint">
                {applied
                    ? 'Очерёдность можно менять только без поиска — нажмите «Сбросить».'
                    : 'Порядок строк — очерёдность запроса реквизитов. Перетащите строку за значок слева или используйте стрелки справа.'}
              </p>
              <div className="table-wrap">
                <table className="grid grid-merchants">
                  <thead>
                  <tr>
                    <th className="c-order" title="Очерёдность" aria-label="Очерёдность" />
                    <th className="c-act">Вкл.</th>
                    <th>Мерчант</th>
                    <th className="c-right">Мин. сумма</th>
                    <th className="c-right">Макс. сумма</th>
                    <th className="c-move" title="Очерёдность" aria-label="Очерёдность" />
                  </tr>
                  </thead>
                  <tbody>
                  {list.map((r, i) => (
                      <tr
                          key={r.id}
                          className={[
                            'no-hover',
                            dragId === r.id ? 'is-dragging' : '',
                            drop?.id === r.id && dragId !== r.id ? (drop.after ? 'drop-after' : 'drop-before') : '',
                          ].filter(Boolean).join(' ')}
                          draggable={canReorder && !moving && armedId === r.id}
                          onDragStart={(e) => {
                            setDragId(r.id);
                            e.dataTransfer.effectAllowed = 'move';
                            e.dataTransfer.setData('text/plain', String(r.id)); // без этого Firefox не начнёт перетаскивание
                          }}
                          onDragOver={(e) => onDragOver(e, r)}
                          onDrop={onDrop}
                          onDragEnd={endDrag}
                      >
                        <td className="c-order">
                        <span
                            className={`drag-handle${canReorder && !moving ? '' : ' is-off'}`}
                            title={canReorder ? 'Перетащите, чтобы изменить очерёдность' : undefined}
                            onMouseDown={() => canReorder && !moving && setArmedId(r.id)}
                            onMouseUp={() => setArmedId(null)}
                        >
                          <i className="fa-solid fa-grip-vertical" />
                        </span>
                        </td>
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
                        {/* Кнопки вверх/вниз — как в вебаппе «Управление мерчантами» */}
                        <td className="c-move">
                          <div className="move-cell">
                            <button
                                type="button" className="icon-btn order-btn"
                                title="Выше" aria-label="Выше"
                                disabled={!canReorder || moving || i === 0}
                                onClick={() => move(i, i - 1)}
                            >
                              <i className="fa-solid fa-chevron-up" />
                            </button>
                            <button
                                type="button" className="icon-btn order-btn"
                                title="Ниже" aria-label="Ниже"
                                disabled={!canReorder || moving || i === list.length - 1}
                                onClick={() => move(i, i + 1)}
                            >
                              <i className="fa-solid fa-chevron-down" />
                            </button>
                          </div>
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
            </>
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
  const [sort, setSort] = useState(null); // null — по умолчанию новые сверху
  const [confirm, setConfirm] = useState(null); // { row, action: 'approve' | 'cancel' }
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (f, pageNum, srt) => {
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
        // Без выбранной сортировки параметр не шлём — api подставит «новые сверху».
        ...(srt ? { sort: sortParam(srt) } : {}),
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

  useEffect(() => { load(applied, page, sort); }, [applied, page, sort, reloadKey, load]);

  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };

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

            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="w-mode">Дата создания</label>
                <select id="w-mode" value={draft.dateMode} onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>

              <div className="date-fields">
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
            <RefreshButton onClick={() => setReloadKey((k) => k + 1)} loading={loading} />
          </div>
        </div>

        <div className="table-wrap">
          <table className="grid grid-withdrawals">
            <thead>
            <tr>
              <SortTh field="clientUsername" sort={sort} onSort={onSort}>Клиент</SortTh>
              <SortTh field="id" sort={sort} onSort={onSort}>ID заявки</SortTh>
              <SortTh field="status" sort={sort} onSort={onSort}>Статус</SortTh>
              <SortTh field="createdAt" sort={sort} onSort={onSort}>Создана</SortTh>
              <SortTh field="grossSourceAmount" sort={sort} onSort={onSort} className="c-right">Сумма заявки, ₽</SortTh>
              <SortTh field="commissionPercent" sort={sort} onSort={onSort} className="c-right">Комиссия</SortTh>
              <SortTh field="netSourceAmount" sort={sort} onSort={onSort} className="c-right">К выводу, ₽</SortTh>
              <SortTh field="rate" sort={sort} onSort={onSort} className="c-right">Курс ₽/USDT</SortTh>
              <SortTh field="targetAmount" sort={sort} onSort={onSort} className="c-right">Сумма, USDT</SortTh>
              <SortTh field="address" sort={sort} onSort={onSort}>Адрес кошелька</SortTh>
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

/* ============================================================
   КБ мерчантов — журнал коллбэков, полученных от мерчантов
   ============================================================
   Только просмотр: фильтр, таблица, постраничная загрузка.
   Подробного просмотра и действий над записями нет (по ТЗ).
   Фильтра по статусу нет (бэк: не нужен). */

const EMPTY_CB_FILTER = {
  orderId: '', merchantOrderId: '', merchant: '',
  dateMode: 'equal',
  date: '', time: '',
  dateFrom: '', timeFrom: '',
  dateTo: '', timeTo: '',
};

function CallbacksSection({ merchants, showToast }) {
  const [draft, setDraft] = useState(EMPTY_CB_FILTER);
  const [applied, setApplied] = useState(EMPTY_CB_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [sort, setSort] = useState(null); // null — по умолчанию новые сверху

  const load = useCallback(async (f, pageNum, srt) => {
    setLoading(true);
    try {
      // Даты — как в остальных разделах: «Равна» без времени — весь день,
      // со временем — ровно эта секунда.
      const range = f.dateMode === 'equal'
          ? {
            createdAtFrom: dateTimeToMs(f.date, f.time, 'start'),
            createdAtTo: dateTimeToMs(f.date, f.time, 'end'),
          }
          : {
            createdAtFrom: dateTimeToMs(f.dateFrom, f.timeFrom, 'start'),
            createdAtTo: dateTimeToMs(f.dateTo, f.timeTo, 'end'),
          };
      const r = await api.merchantCallbacks({
        page: pageNum,
        size: PAGE_SIZE,
        orderId: f.orderId.trim(),
        merchantOrderId: f.merchantOrderId.trim(),
        merchant: f.merchant,
        ...range,
        // Без выбранной сортировки параметр не шлём — api подставит «новые сверху».
        ...(srt ? { sort: sortParam(srt) } : {}),
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить коллбэки', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page, sort); }, [applied, page, sort, load]);

  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };

  // orderId у бэка — UUID: неполный ID он не примет (ошибка конвертации),
  // поэтому проверяем формат до запроса, как в разделе «Клиенты».
  const orderIdInvalid = draft.orderId.trim() !== '' && !isUuid(draft.orderId);

  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));
  const search = () => { if (orderIdInvalid) return; setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setDraft(EMPTY_CB_FILTER); setApplied(EMPTY_CB_FILTER); };
  const onEnter = (e) => { if (e.key === 'Enter') search(); };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  // Мерчанты приходят кодом (ALFA_TEAM), человеку показываем displayName.
  const merchantLabel = (name) =>
      merchants.find((m) => m.name === name)?.displayName || name || '—';

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid filter-grid-3">
            <div className="field">
              <label htmlFor="cb-order">ID ордера</label>
              <input id="cb-order" value={draft.orderId} placeholder="UUID ордера"
                     className={orderIdInvalid ? 'invalid' : ''}
                     onChange={(e) => set('orderId', e.target.value)} onKeyDown={onEnter} />
              {orderIdInvalid && <span className="field-error">ID должен соответствовать формату UUID</span>}
            </div>

            <div className="field">
              <label htmlFor="cb-morder">ID у мерчанта</label>
              <input id="cb-morder" value={draft.merchantOrderId}
                     placeholder="Идентификатор заказа у мерчанта"
                     onChange={(e) => set('merchantOrderId', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="cb-merchant">Мерчант</label>
              <select id="cb-merchant" value={draft.merchant}
                      onChange={(e) => set('merchant', e.target.value)}>
                <option value="">Все</option>
                {merchants.map((m) => (
                    <option key={m.name} value={m.name}>{m.displayName || m.name}</option>
                ))}
              </select>
            </div>

            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="cb-mode">Дата создания</label>
                <select id="cb-mode" value={draft.dateMode} onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>

              <div className="date-fields">
                {draft.dateMode === 'equal' ? (
                    <DateTimeField
                        id="cb-date" label="Дата"
                        date={draft.date} time={draft.time}
                        onDate={(v) => set('date', v)} onTime={(v) => set('time', v)}
                    />
                ) : (
                    <>
                      <DateTimeField
                          id="cb-from" label="С"
                          date={draft.dateFrom} time={draft.timeFrom}
                          onDate={(v) => set('dateFrom', v)} onTime={(v) => set('timeFrom', v)}
                      />
                      <DateTimeField
                          id="cb-to" label="По"
                          date={draft.dateTo} time={draft.timeTo}
                          onDate={(v) => set('dateTo', v)} onTime={(v) => set('timeTo', v)}
                      />
                    </>
                )}
              </div>
            </div>
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={loading || orderIdInvalid}>
              <i className="fa-solid fa-magnifying-glass" />
              Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset} disabled={loading}>
              Сбросить
            </button>
            <RefreshButton onClick={() => load(applied, page, sort)} loading={loading} />
          </div>
        </div>

        <div className="table-wrap">
          <table className="grid grid-callbacks">
            <thead>
            <tr>
              <SortTh field="createdAt" sort={sort} onSort={onSort}>Создан</SortTh>
              <SortTh field="orderId" sort={sort} onSort={onSort}>ID ордера</SortTh>
              <SortTh field="merchant" sort={sort} onSort={onSort}>Мерчант</SortTh>
              <SortTh field="merchantOrderId" sort={sort} onSort={onSort}>ID у мерчанта</SortTh>
              <SortTh field="status" sort={sort} onSort={onSort}>Статус</SortTh>
              <SortTh field="statusDescription" sort={sort} onSort={onSort}>Описание статуса</SortTh>
              <SortTh field="processedAt" sort={sort} onSort={onSort}>Обработан</SortTh>
            </tr>
            </thead>
            <tbody>
            {rows.map((c, i) => (
                <tr key={c.id || `${c.orderId}-${c.createdAt}-${i}`} className="no-hover">
                  <td className="mono nowrap">{fmtDateTime(c.createdAt)}</td>
                  <td className="c-id"><CopyValue value={c.orderId} onCopy={copy} /></td>
                  <td>{merchantLabel(c.merchant)}</td>
                  <td className="mono">{c.merchantOrderId || '—'}</td>
                  {/* Статус — строка от мерчанта, у каждого мерчанта свои значения;
                        показываем как пришла, без перевода и цветов (решение бэка). */}
                  <td className="mono">{c.status || '—'}</td>
                  <td>{c.statusDescription || '—'}</td>
                  {/* Время обработки КБ (processedAt); пока не обработан — прочерк */}
                  <td className="mono nowrap">{fmtDateTime(c.processedAt)}</td>
                </tr>
            ))}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Коллбэков не найдено</div>
          )}
          {loading && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего коллбэков"
        />
      </>
  );
}

/* ==================== Раздел «Инциденты» ==================== */
/* Инциденты системы: ситуации, которые бэк не смог обработать сам
   (не обновился баланс, не задана комиссия клиента и т. п.).
   Фильтр, таблица с сортировкой и пагинацией, карточка по двойному клику
   и обработка (NEW → RESOLVED) с необязательным комментарием.
   Подписи типов и статусов — из словаря: IncidentType { name, summary,
   description, guide }, IncidentStatus { name, description }. */

const EMPTY_INC_FILTER = {
  id: '', status: '', type: '',
  // Дата создания
  dateMode: 'equal',
  date: '', time: '',
  dateFrom: '', timeFrom: '',
  dateTo: '', timeTo: '',
  // Дата обработки
  rDateMode: 'equal',
  rDate: '', rTime: '',
  rDateFrom: '', rTimeFrom: '',
  rDateTo: '', rTimeTo: '',
};

// По ТЗ записи идут от новых к старым — это и есть сортировка по умолчанию.
const INC_DEFAULT_SORT = { field: 'createdAt', dir: 'desc' };

const INC_COMMENT_MAX = 1000;

/* Переход к связанной сущности из карточки инцидента — по типу инцидента.
   key — ключ в data, откуда берётся идентификатор. Для КБ мерчанта в data
   лежит ID ордера у мерчанта, поэтому ордер ищется по merchantOrderId. */
const INCIDENT_LINKS = {
  BALANCE_UPDATE_ERROR: { kind: 'client', key: 'clientId' },
  TRANSACTION_CREATION_ERROR: { kind: 'order', key: 'orderId' },
  CLIENT_COMMISSION_PERCENT_NOT_SET: { kind: 'client', key: 'clientId' },
  CLIENT_MIN_WITHDRAWAL_AMOUNT_NOT_SET: { kind: 'client', key: 'clientId' },
  MERCHANT_CALLBACK_HANDLE_ERROR: { kind: 'merchantOrder', key: 'merchantOrderId' },
  ORDER_STATUS_TRANSITION_REJECTED: { kind: 'order', key: 'orderId' },
};

const incidentStatusMod = (name) => (name === 'NEW' ? 'info' : name === 'RESOLVED' ? 'ok' : 'neutral');

/* Диапазон дат фильтра -> { from, to } в миллисекундах.
   p — префикс полей черновика: '' для даты создания, 'r' для даты обработки. */
function incRange(f, p) {
  const k = (name) => (p ? p + name.charAt(0).toUpperCase() + name.slice(1) : name);
  return f[k('dateMode')] === 'equal'
      ? { from: dateTimeToMs(f[k('date')], f[k('time')], 'start'), to: dateTimeToMs(f[k('date')], f[k('time')], 'end') }
      : { from: dateTimeToMs(f[k('dateFrom')], f[k('timeFrom')], 'start'), to: dateTimeToMs(f[k('dateTo')], f[k('timeTo')], 'end') };
}

function IncidentsSection({ incidentStatuses, incidentTypes, orderStatuses, merchants, methods, showToast, canOpenClient }) {
  const [draft, setDraft] = useState(EMPTY_INC_FILTER);
  const [applied, setApplied] = useState(EMPTY_INC_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [sort, setSort] = useState(INC_DEFAULT_SORT);
  const [reloadKey, setReloadKey] = useState(0);
  const [card, setCard] = useState(null);         // открытая карточка инцидента
  const [resolving, setResolving] = useState(null); // инцидент в окне обработки

  const load = useCallback(async (f, pageNum, srt) => {
    setLoading(true);
    try {
      const created = incRange(f, '');
      const resolved = incRange(f, 'r');
      const r = await api.incidents({
        page: pageNum,
        size: PAGE_SIZE,
        id: f.id.trim(),
        status: f.status,
        type: f.type,
        createdAtFrom: created.from,
        createdAtTo: created.to,
        resolvedAtFrom: resolved.from,
        resolvedAtTo: resolved.to,
        // Без выбранной сортировки параметр не шлём — api подставит «новые сверху».
        ...(srt ? { sort: sortParam(srt) } : {}),
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить инциденты', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page, sort); }, [applied, page, sort, reloadKey, load]);

  // ID инцидента — число: буквы бэк не примет, проверяем до запроса.
  const idInvalid = draft.id.trim() !== '' && !/^\d+$/.test(draft.id.trim());

  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));
  const search = () => { if (idInvalid) return; setPage(0); setApplied(draft); };
  const reset = () => {
    setPage(0); setSort(INC_DEFAULT_SORT); setDraft(EMPTY_INC_FILTER); setApplied(EMPTY_INC_FILTER);
  };
  const onEnter = (e) => { if (e.key === 'Enter') search(); };
  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const typeOf = (name) => incidentTypes.find((t) => t.name === name);
  // В таблице — краткое описание типа (summary), а не константа и не description.
  const typeLabel = (name) => typeOf(name)?.summary || name || '—';
  const statusLabel = (name) => incidentStatuses.find((s) => s.name === name)?.description || name || '—';

  const onResolved = () => {
    setResolving(null);
    setCard(null);
    showToast('Инцидент обработан', 'success');
    setReloadKey((k) => k + 1);
  };

  // Блок даты фильтра: «Равна» / «Диапазон», время необязательно — как в остальных разделах.
  const dateGroup = (label, idp, p) => {
    const k = (name) => (p ? p + name.charAt(0).toUpperCase() + name.slice(1) : name);
    const mode = draft[k('dateMode')];
    return (
        <div className={`date-group${mode === 'range' ? ' date-group-range' : ''}`}>
          <div className="field">
            <label htmlFor={`${idp}-mode`}>{label}</label>
            <select id={`${idp}-mode`} value={mode} onChange={(e) => set(k('dateMode'), e.target.value)}>
              <option value="equal">Равна</option>
              <option value="range">Диапазон</option>
            </select>
          </div>
          <div className="date-fields">
            {mode === 'equal' ? (
                <DateTimeField
                    id={`${idp}-date`} label="Дата"
                    date={draft[k('date')]} time={draft[k('time')]}
                    onDate={(v) => set(k('date'), v)} onTime={(v) => set(k('time'), v)}
                />
            ) : (
                <>
                  <DateTimeField
                      id={`${idp}-from`} label="С"
                      date={draft[k('dateFrom')]} time={draft[k('timeFrom')]}
                      onDate={(v) => set(k('dateFrom'), v)} onTime={(v) => set(k('timeFrom'), v)}
                  />
                  <DateTimeField
                      id={`${idp}-to`} label="По"
                      date={draft[k('dateTo')]} time={draft[k('timeTo')]}
                      onDate={(v) => set(k('dateTo'), v)} onTime={(v) => set(k('timeTo'), v)}
                  />
                </>
            )}
          </div>
        </div>
    );
  };

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid filter-grid-3">
            <div className="field">
              <label htmlFor="inc-id">ID инцидента</label>
              <input id="inc-id" value={draft.id} placeholder="Номер инцидента" inputMode="numeric"
                     className={idInvalid ? 'invalid' : ''}
                     onChange={(e) => set('id', e.target.value)} onKeyDown={onEnter} />
              {idInvalid && <span className="field-error">ID инцидента — целое число</span>}
            </div>

            <div className="field">
              <label htmlFor="inc-status">Статус</label>
              <select id="inc-status" value={draft.status} onChange={(e) => set('status', e.target.value)}>
                <option value="">Все</option>
                {incidentStatuses.map((s) => (
                    <option key={s.name} value={s.name}>{s.description || s.name}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="inc-type">Тип</label>
              <select id="inc-type" value={draft.type} onChange={(e) => set('type', e.target.value)}>
                <option value="">Все</option>
                {incidentTypes.map((t) => (
                    <option key={t.name} value={t.name}>{t.summary || t.name}</option>
                ))}
              </select>
            </div>

            {dateGroup('Дата создания', 'inc-c', '')}
            {dateGroup('Дата обработки', 'inc-r', 'r')}
          </div>

          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={search} disabled={loading || idInvalid}>
              <i className="fa-solid fa-magnifying-glass" />
              Поиск
            </button>
            <button type="button" className="btn btn-secondary" onClick={reset} disabled={loading}>
              Сбросить
            </button>
            <RefreshButton onClick={() => setReloadKey((k) => k + 1)} loading={loading} />
          </div>
        </div>

        <p className="table-hint">Двойной клик по строке — подробная информация по инциденту</p>

        <div className="table-wrap">
          <table className="grid grid-incidents">
            <thead>
            <tr>
              <SortTh field="id" sort={sort} onSort={onSort}>ID</SortTh>
              <SortTh field="createdAt" sort={sort} onSort={onSort}>Создан</SortTh>
              <SortTh field="status" sort={sort} onSort={onSort}>Статус</SortTh>
              <SortTh field="errorTimestamp" sort={sort} onSort={onSort}>Время ошибки</SortTh>
              <SortTh field="type" sort={sort} onSort={onSort}>Тип</SortTh>
              <SortTh field="resolvedById" sort={sort} onSort={onSort}>Обработал</SortTh>
              <SortTh field="resolvedAt" sort={sort} onSort={onSort}>Обработан</SortTh>
              <th className="c-act" aria-label="Действия" />
            </tr>
            </thead>
            <tbody>
            {rows.map((r) => (
                <tr key={r.id} onDoubleClick={() => setCard(r)} title="Двойной клик — карточка инцидента">
                  <td className="mono strong">{r.id}</td>
                  <td className="mono nowrap">{fmtDateTime(r.createdAt)}</td>
                  <td>
                    <span className={`badge badge-${incidentStatusMod(r.status)}`}>{statusLabel(r.status)}</span>
                  </td>
                  <td className="mono nowrap">{fmtDateTime(r.errorTimestamp)}</td>
                  <td className="inc-type">{typeLabel(r.type)}</td>
                  <td className="c-id"><CopyValue value={r.resolvedById} onCopy={copy} /></td>
                  <td className="mono nowrap">{fmtDateTime(r.resolvedAt)}</td>
                  <td className="c-act">
                    {r.status === 'NEW' && (
                        <button
                            type="button"
                            className="icon-btn icon-btn-resolve"
                            title="Обработать"
                            aria-label={`Обработать инцидент ${r.id}`}
                            onClick={(e) => { e.stopPropagation(); setResolving(r); }}
                            onDoubleClick={(e) => e.stopPropagation()}
                        >
                          <i className="fa-solid fa-screwdriver-wrench" />
                        </button>
                    )}
                  </td>
                </tr>
            ))}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Инцидентов не найдено</div>
          )}
          {loading && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего инцидентов"
        />

        {card && (
            <IncidentCard
                incident={card}
                type={typeOf(card.type)}
                orderStatuses={orderStatuses}
                merchants={merchants}
                methods={methods}
                showToast={showToast}
                canOpenClient={canOpenClient}
                onCopy={copy}
                onResolve={() => setResolving(card)}
                blocked={Boolean(resolving)}
                onClose={() => setCard(null)}
            />
        )}
        {resolving && (
            <ResolveIncidentDialog
                incident={resolving}
                typeLabel={typeLabel(resolving.type)}
                showToast={showToast}
                onCancel={() => setResolving(null)}
                onResolved={onResolved}
                onFailed={() => { setResolving(null); setReloadKey((k) => k + 1); }}
            />
        )}
      </>
  );
}

/* Текст описания с подставленными значениями.
   В description плейсхолдеры вида ${key}; значение берётся из data[key]
   и выводится отдельной меткой с копированием. Ключа нет в data —
   оставляем плейсхолдер как есть, чтобы было видно, чего не хватает. */
function IncidentText({ template, data, onCopy }) {
  if (!template) return <span className="dim">—</span>;
  const parts = String(template).split(/\$\{([^}]+)\}/);
  const renderValue = (key) => {
    const value = data?.[key];
    if (value === undefined || value === null || value === '') {
      return <span className="mono dim">{`\${${key}}`}</span>;
    }
    return (
        <span className="inc-chip" title="Скопировать" onClick={() => onCopy(value)}>
          <span className="mono">{String(value)}</span>
          <i className="fa-regular fa-copy" />
        </span>
    );
  };
  const nodes = [];
  let text = parts[0];
  for (let i = 1; i < parts.length; i += 2) {
    const before = text.match(/\S*$/)[0];
    const after = parts[i + 1].match(/^\S*/)[0];
    const head = text.slice(0, text.length - before.length);
    if (head) nodes.push(<span key={`t${i}`}>{head}</span>);
    nodes.push(<span key={i} className="inc-nowrap">{before}{renderValue(parts[i])}{after}</span>);
    text = parts[i + 1].slice(after.length);
  }
  if (text) nodes.push(<span key="tail">{text}</span>);
  return <>{nodes}</>;
}

/* ---- Карточка инцидента ----
   Описание (description с подстановкой из data), «Что необходимо сделать»
   (guide), комментарий обработавшего (только если он есть) и кнопка
   перехода к связанной сущности по типу инцидента.
   У нового инцидента в подвале — «Обработать» слева от «Закрыть»: открывает
   то же окно обработки, что кнопка в строке таблицы (onResolve).
   blocked — поверх открыто окно обработки: Escape карточку не закрывает. */
function IncidentCard({ incident, type, orderStatuses, merchants, methods, showToast, canOpenClient, onCopy, onResolve, blocked, onClose }) {
  const [client, setClient] = useState(null); // карточка клиента поверх
  const [order, setOrder] = useState(null);   // карточка ордера поверх
  const [opening, setOpening] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !client && !order && !blocked) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, client, order, blocked]);

  const link = INCIDENT_LINKS[incident.type];
  const linkId = link ? incident.data?.[link.key] : null;
  // «Карточка клиента» ведёт в раздел «Клиенты» — оператору её не показываем (как в карточке ордера).
  const showLink = Boolean(link && linkId && (link.kind !== 'client' || canOpenClient));

  const openLink = async () => {
    setOpening(true);
    try {
      if (link.kind === 'client') {
        const r = await api.clients({ id: linkId, page: 0, size: 1 });
        if (!r.items[0]) { showToast('Клиент не найден', 'error'); return; }
        setClient(r.items[0]);
      } else if (link.kind === 'order') {
        const o = await api.order(linkId);
        if (!o) { showToast('Ордер не найден', 'error'); return; }
        setOrder(o);
      } else {
        // В data — ID ордера у мерчанта: ищем ордер по нему.
        const r = await api.orders({ merchantOrderId: linkId, page: 0, size: 1 });
        if (!r.items[0]) { showToast('Ордер не найден', 'error'); return; }
        setOrder(r.items[0]);
      }
    } catch (e) {
      showToast(e.message || 'Не удалось открыть карточку', 'error');
    } finally {
      setOpening(false);
    }
  };

  return (
      <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
        <div className="modal inc-card" role="dialog" aria-modal="true" aria-labelledby="inc-title">
          <div className="ocard-head">
            <span className="ocard-ico inc-ico"><i className="fa-solid fa-triangle-exclamation" /></span>
            <div className="ocard-title">
              <span className="ocard-name" id="inc-title">Инцидент №{incident.id}</span>
              <span className="ocard-sub">
                {type?.summary || incident.type || '—'} · {fmtDateTime(incident.createdAt)}
              </span>
            </div>
            <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
          </div>

          <div className="modal-body">
            <h3 className="eyebrow inc-label">Описание инцидента</h3>
            <div className="inc-box">
              <IncidentText template={type?.description} data={incident.data} onCopy={onCopy} />
            </div>

            {type?.guide && (
                <>
                  <h3 className="eyebrow inc-label">Что необходимо сделать</h3>
                  <div className="inc-box">
                    <IncidentText template={type.guide} data={incident.data} onCopy={onCopy} />
                  </div>
                </>
            )}

            {incident.comment && (
                <>
                  <h3 className="eyebrow inc-label">Комментарий</h3>
                  <div className="inc-box inc-comment">{incident.comment}</div>
                </>
            )}

            {showLink && (
                <button type="button" className="btn btn-outline inc-link" onClick={openLink} disabled={opening}>
                  {opening && <i className="fa-solid fa-spinner fa-spin" />}
                  {link.kind === 'client' ? 'Карточка клиента' : 'Карточка ордера'}
                </button>
            )}
          </div>

          <div className="modal-foot">
            {incident.status === 'NEW' && (
                <button type="button" className="btn btn-primary" onClick={onResolve}>
                  <i className="fa-solid fa-screwdriver-wrench" />
                  Обработать
                </button>
            )}
            <button
                type="button"
                className={`btn ${incident.status === 'NEW' ? 'btn-secondary' : 'btn-primary'}`}
                onClick={onClose}
            >
              Закрыть
            </button>
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
        {order && (
            <OrderCard
                order={order}
                orderStatuses={orderStatuses}
                merchants={merchants}
                methods={methods}
                onClose={() => setOrder(null)}
                onCopy={onCopy}
                showToast={showToast}
                canOpenClient={canOpenClient}
            />
        )}
      </div>
  );
}

/* ---- Окно обработки инцидента: необязательный комментарий ---- */
function ResolveIncidentDialog({ incident, typeLabel, showToast, onCancel, onResolved, onFailed }) {
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  const apply = async () => {
    setBusy(true);
    try {
      await api.resolveIncident(incident.id, comment.trim());
      onResolved();
    } catch (e) {
      // Уже обработанный инцидент бэк сейчас отдаёт 500 без пояснения («Contact support.»),
      // поэтому подсказываем вероятную причину и перечитываем таблицу.
      showToast(
          e.status === 500
              ? 'Не удалось обработать: возможно, инцидент уже обработан'
              : (e.message || 'Не удалось обработать инцидент'),
          'error',
      );
      setBusy(false);
      onFailed();
    }
  };

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm" role="dialog" aria-modal="true" aria-labelledby="ri-title">
          <div className="ocard-head">
            <span className="ocard-ico"><i className="fa-solid fa-check" /></span>
            <div className="ocard-title">
              <span className="ocard-name" id="ri-title">Обработка инцидента</span>
              <span className="ocard-sub">Инцидент №{incident.id} · {typeLabel}</span>
            </div>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <label className="eyebrow inc-label" htmlFor="ri-comment">Комментарий</label>
            <textarea
                id="ri-comment"
                className="inc-textarea"
                rows={3}
                maxLength={INC_COMMENT_MAX}
                placeholder="Что было сделано"
                value={comment}
                disabled={busy}
                autoFocus
                onChange={(e) => setComment(e.target.value)}
            />
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Отмена</button>
            <button type="button" className="btn btn-primary" onClick={apply} disabled={busy}>
              {busy && <i className="fa-solid fa-spinner fa-spin" />}
              Обработать
            </button>
          </div>
        </div>
      </div>
  );
}

/* ---- «Обновить» в форме поиска ----
   Есть во всех разделах с таблицами: перечитывает текущую страницу с уже
   применённым фильтром и сортировкой (что набрано в полях, но не применено
   «Поиском», не учитывается). Стоит в одной строке с «Поиск»/«Сбросить»,
   чтобы не занимать место над таблицей. */
function RefreshButton({ onClick, loading }) {
  return (
      <button type="button" className="btn btn-secondary" onClick={onClick} disabled={loading} title="Перечитать данные">
        <i className={`fa-solid fa-rotate-right${loading ? ' fa-spin' : ''}`} />
        Обновить
      </button>
  );
}

/* ---- Дата и необязательное время двумя полями рядом ----
   Общий для всех фильтров кабинета: «Клиенты», «Ордера», «Транзакции»,
   «Заявки на вывод», «КБ мерчантов», «Инциденты». */
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

/* ==================== Раздел «История мерчантов» ====================
   Выдачи реквизитов мерчантами по запросам клиентов API (таблица
   merchant_history, только записи initiator_app = processing — их
   отбирает бэк). Только просмотр.
   Сортирует бэк по всей выборке; по умолчанию — дата по убыванию. */

const EMPTY_MH_FILTER = {
  operationId: '', merchantOrderId: '', actorId: '',
  merchants: [], details: '', amount: '',
  dateMode: 'equal',
  date: '', time: '',
  dateFrom: '', timeFrom: '',
  dateTo: '', timeTo: '',
};

const MH_DEFAULT_SORT = { field: 'createdAt', dir: 'desc' };

/* Деньги в таблице — с копейками, без знака валюты (он в заголовке не нужен по макету). */
function fmtMh(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  return Number.isFinite(n)
      ? n.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '—';
}

function MerchantHistorySection({ merchants, methods, showToast }) {
  const [draft, setDraft] = useState(EMPTY_MH_FILTER);
  const [applied, setApplied] = useState(EMPTY_MH_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(null); // null — сортировка по умолчанию
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0); // «Обновить» — тот же запрос заново

  const load = useCallback(async (f, pageNum, srt) => {
    setLoading(true);
    try {
      const range = f.dateMode === 'equal'
          ? {
            createdAtFrom: dateTimeToMs(f.date, f.time, 'start'),
            createdAtTo: dateTimeToMs(f.date, f.time, 'end'),
          }
          : {
            createdAtFrom: dateTimeToMs(f.dateFrom, f.timeFrom, 'start'),
            createdAtTo: dateTimeToMs(f.dateTo, f.timeTo, 'end'),
          };
      const r = await api.merchantHistory({
        page: pageNum,
        size: PAGE_SIZE,
        sort: sortParam(srt || MH_DEFAULT_SORT),
        operationId: f.operationId.trim(),
        // В API бэка ID у мерчанта называется orderId.
        orderId: f.merchantOrderId.trim(),
        actorId: f.actorId.trim(),
        details: f.details.trim(),
        merchants: f.merchants,
        amount: f.amount,
        ...range,
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить историю', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page, sort); }, [applied, page, sort, reloadKey, load]);

  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));
  const search = () => { setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setSort(null); setDraft(EMPTY_MH_FILTER); setApplied(EMPTY_MH_FILTER); };
  const onEnter = (e) => { if (e.key === 'Enter') search(); };
  const onSort = (field) => { setSort((s) => nextSort(s, field)); setPage(0); };
  const refresh = () => setReloadKey((k) => k + 1);

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const merchantLabel = (name) =>
      merchants.find((m) => m.name === name)?.displayName || name || '—';
  const methodLabel = (name) =>
      methods.find((m) => m.name === name)?.description || name || '—';

  const shownSort = sort || MH_DEFAULT_SORT;
  const th = (field, title, tip, className) => (
      <SortTh field={field} sort={shownSort} onSort={onSort} className={className}>
        {tip ? <span title={tip}>{title}</span> : title}
      </SortTh>
  );

  return (
      <>
        <div className="card filter-card">
          <div className="filter-grid filter-grid-3">
            <div className="field">
              <label htmlFor="mh-op">ID ордера</label>
              <input id="mh-op" value={draft.operationId} placeholder="UUID ордера в процессинге"
                     onChange={(e) => set('operationId', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="mh-morder">ID у мерчанта</label>
              <input id="mh-morder" value={draft.merchantOrderId} placeholder="Идентификатор заказа у мерчанта"
                     onChange={(e) => set('merchantOrderId', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="mh-actor">Клиент</label>
              <input id="mh-actor" value={draft.actorId} placeholder="UUID клиента"
                     onChange={(e) => set('actorId', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="mh-merchant">Мерчант</label>
              <MultiSelect
                  id="mh-merchant"
                  options={merchants.map((m) => ({ value: m.name, label: m.displayName || m.name }))}
                  value={draft.merchants}
                  onChange={(v) => set('merchants', v)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-details">Реквизиты</label>
              <input id="mh-details" value={draft.details} placeholder="Банк или номер"
                     onChange={(e) => set('details', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="mh-amount">Сумма</label>
              <input id="mh-amount" value={draft.amount} placeholder="целое число" inputMode="numeric"
                     onChange={(e) => set('amount', e.target.value.replace(/\D/g, ''))} onKeyDown={onEnter} />
            </div>

            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="mh-mode">Дата создания</label>
                <select id="mh-mode" value={draft.dateMode} onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>

              <div className="date-fields">
                {draft.dateMode === 'equal' ? (
                    <DateTimeField
                        id="mh-date" label="Дата"
                        date={draft.date} time={draft.time}
                        onDate={(v) => set('date', v)} onTime={(v) => set('time', v)}
                    />
                ) : (
                    <>
                      <DateTimeField
                          id="mh-from" label="С"
                          date={draft.dateFrom} time={draft.timeFrom}
                          onDate={(v) => set('dateFrom', v)} onTime={(v) => set('timeFrom', v)}
                      />
                      <DateTimeField
                          id="mh-to" label="По"
                          date={draft.dateTo} time={draft.timeTo}
                          onDate={(v) => set('dateTo', v)} onTime={(v) => set('timeTo', v)}
                      />
                    </>
                )}
              </div>
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
            <RefreshButton onClick={refresh} loading={loading} />
          </div>
        </div>

        <div className="table-wrap">
          <table className="grid grid-mh">
            <thead>
            <tr>
              {th('operationId', 'ID ордера', 'Идентификатор ордера')}
              {th('createdAt', 'Дата и время')}
              {th('details', 'Реквизиты')}
              {th('merchant', 'Мерчант')}
              {th('merchantAmount', 'Мерч.сумма', 'Мерчантская сумма', 'c-right')}
              {th('requestedAmount', 'Запр.сумма', 'Запрошенная сумма', 'c-right')}
              {th('method', 'Метод')}
              {th('merchantOrderId', 'ID у мерчанта', 'Идентификатор заказа у мерчанта')}
              {th('actorId', 'Клиент', 'Идентификатор клиента API, запросившего реквизиты')}
            </tr>
            </thead>
            <tbody>
            {rows.map((r, i) => (
                <tr key={`${r.operationId}-${r.createdAt}-${i}`} className="no-hover">
                  <td className="c-id"><CopyValue value={r.operationId} onCopy={copy} /></td>
                  <td className="mono nowrap">{fmtDateTime(r.createdAt)}</td>
                  <td>{r.details || '—'}</td>
                  <td>{merchantLabel(r.merchant)}</td>
                  <td className="c-right mono">{fmtMh(r.merchantAmount)}</td>
                  <td className="c-right mono">{fmtMh(r.requestedAmount)}</td>
                  <td>{r.method ? <span className="badge badge-info">{methodLabel(r.method)}</span> : '—'}</td>
                  <td className="c-id"><CopyValue value={r.merchantOrderId} onCopy={copy} /></td>
                  <td className="c-id"><CopyValue value={r.actorId} onCopy={copy} /></td>
                </tr>
            ))}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Записей не найдено</div>
          )}
          {loading && rows.length === 0 && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего записей"
        />
      </>
  );
}

/* ---- Выпадающий список с множественным выбором ----
   Кнопка с перечнем выбранного («Все», если ничего не отмечено) и
   список с флажками и поиском — мерчантов больше 80. */
function MultiSelect({ id, options, value, onChange, placeholder = 'Все' }) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const toggle = (v) => onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v]);
  const labels = options.filter((o) => value.includes(o.value)).map((o) => o.label);
  const query = q.trim().toLowerCase();
  const shown = query
      ? options.filter((o) => o.label.toLowerCase().includes(query) || String(o.value).toLowerCase().includes(query))
      : options;

  return (
      <div className="ms" ref={ref}>
        <button id={id} type="button" className={`ms-control${value.length ? ' filled' : ''}`}
                onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="listbox"
                title={labels.join(', ')}>
          <span className="ms-value">{labels.length ? labels.join(', ') : placeholder}</span>
          {value.length > 1 && <span className="ms-count">{value.length}</span>}
        </button>
        {open && (
            <div className="ms-dropdown">
              <input className="ms-search" value={q} placeholder="Поиск" autoFocus
                     onChange={(e) => setQ(e.target.value)} />
              {value.length > 0 && (
                  <button type="button" className="ms-clear" onClick={() => onChange([])}>
                    Сбросить выбор
                  </button>
              )}
              <div className="ms-list" role="listbox" aria-multiselectable="true">
                {shown.length === 0 && <div className="ms-empty">Ничего не найдено</div>}
                {shown.map((o) => (
                    <label key={o.value} className="ms-option">
                      <input type="checkbox" checked={value.includes(o.value)} onChange={() => toggle(o.value)} />
                      <span>{o.label}</span>
                    </label>
                ))}
              </div>
            </div>
        )}
      </div>
  );
}

/* ==================== Раздел «Транзакции» ==================== */
/* Транзакции всех клиентов (сервис billing): фильтр, таблица и ручная
   корректировка баланса. Устроен по образцу раздела «Ордера».
   Сортировки по клику на заголовок нет — по ТЗ не предусмотрена. */

const EMPTY_TX_FILTER = {
  id: '',
  client: '',
  dateMode: 'equal',
  date: '', time: '',
  dateFrom: '', timeFrom: '',
  dateTo: '', timeTo: '',
};

// Запасные подписи, если словарь не загрузился.
const FALLBACK_OPERATIONS = [
  { name: 'CREDIT', description: 'Зачисление' },
  { name: 'DEBIT', description: 'Списание' },
];

// Цвет метки типа транзакции: зелёный, красный, коричневый — по ТЗ.
const TX_TYPE_MOD = {
  ORDER_CONFIRMATION: 'ok',
  CLIENT_WITHDRAWAL: 'bad',
  MANUAL_CORRECT: 'brown',
};

function TransactionsSection({ operations, txTypes, showToast }) {
  const [draft, setDraft] = useState(EMPTY_TX_FILTER);
  const [applied, setApplied] = useState(EMPTY_TX_FILTER);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async (f, pageNum) => {
    setLoading(true);
    try {
      // Можно заполнить только одну границу диапазона — вторая не уходит.
      const range = f.dateMode === 'equal'
          ? {
            createdAtFrom: dateTimeToMs(f.date, f.time, 'start'),
            createdAtTo: dateTimeToMs(f.date, f.time, 'end'),
          }
          : {
            createdAtFrom: dateTimeToMs(f.dateFrom, f.timeFrom, 'start'),
            createdAtTo: dateTimeToMs(f.dateTo, f.timeTo, 'end'),
          };
      const r = await api.transactions({
        page: pageNum,
        size: PAGE_SIZE,
        id: f.id.trim(),
        // Одно поле на логин и ID клиента — бэк ищет по обоим.
        client: f.client.trim(),
        ...range,
      });
      setRows(r.items);
      setTotal(r.total);
    } catch (e) {
      setRows([]);
      setTotal(0);
      showToast(e.message || 'Не удалось загрузить транзакции', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(applied, page); }, [applied, page, reloadKey, load]);

  const set = (k, v) => setDraft((prev) => ({ ...prev, [k]: v }));
  const search = () => { setPage(0); setApplied(draft); };
  const reset = () => { setPage(0); setDraft(EMPTY_TX_FILTER); setApplied(EMPTY_TX_FILTER); };
  const onEnter = (e) => { if (e.key === 'Enter') search(); };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(String(text ?? ''));
      showToast('Скопировано', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  const opList = operations.length ? operations : FALLBACK_OPERATIONS;
  const opLabel = (name) => opList.find((o) => o.name === name)?.description || name || '—';
  const typeLabel = (name) => txTypes.find((t) => t.name === name)?.description || name || '—';

  // После создания новая транзакция должна оказаться первой строкой.
  const onCreated = () => {
    setCreating(false);
    showToast('Транзакция создана', 'success');
    setPage(0);
    setReloadKey((k) => k + 1);
  };

  return (
      <>
        <div className="card filter-card">
          {/* Четыре колонки: ID, клиент и блок даты (занимает две). */}
          <div className="filter-grid">
            <div className="field">
              <label htmlFor="t-id">ID транзакции</label>
              <input id="t-id" value={draft.id} placeholder="UUID транзакции"
                     onChange={(e) => set('id', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className="field">
              <label htmlFor="t-client">Клиент</label>
              <input id="t-client" value={draft.client} placeholder="ID или логин клиента"
                     onChange={(e) => set('client', e.target.value)} onKeyDown={onEnter} />
            </div>

            <div className={`date-group${draft.dateMode === 'range' ? ' date-group-range' : ''}`}>
              <div className="field">
                <label htmlFor="t-mode">Дата создания</label>
                <select id="t-mode" value={draft.dateMode} onChange={(e) => set('dateMode', e.target.value)}>
                  <option value="equal">Равна</option>
                  <option value="range">Диапазон</option>
                </select>
              </div>

              <div className="date-fields">
                {draft.dateMode === 'equal' ? (
                    <DateTimeField
                        id="t-date" label="Дата"
                        date={draft.date} time={draft.time}
                        onDate={(v) => set('date', v)} onTime={(v) => set('time', v)}
                    />
                ) : (
                    <>
                      <DateTimeField
                          id="t-from" label="С"
                          date={draft.dateFrom} time={draft.timeFrom}
                          onDate={(v) => set('dateFrom', v)} onTime={(v) => set('timeFrom', v)}
                      />
                      <DateTimeField
                          id="t-to" label="По"
                          date={draft.dateTo} time={draft.timeTo}
                          onDate={(v) => set('dateTo', v)} onTime={(v) => set('timeTo', v)}
                      />
                    </>
                )}
              </div>
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
            <RefreshButton onClick={() => setReloadKey((k) => k + 1)} loading={loading} />
            <button type="button" className="btn btn-primary filter-actions-right" onClick={() => setCreating(true)}>
              <i className="fa-solid fa-plus" />
              Создать транзакцию
            </button>
          </div>
        </div>

        <div className="table-wrap">
          <table className="grid grid-tx">
            <thead>
            <tr>
              <th>ID транзакции</th>
              <th>Клиент</th>
              <th>Создана</th>
              <th>Операция</th>
              <th className="c-right">Сумма, ₽</th>
              <th>Тип</th>
              <th>Комментарий</th>
            </tr>
            </thead>
            <tbody>
            {rows.map((t, i) => {
              // Операция определяет оформление строки: CREDIT — зеленоватый
              // фон и «+» зелёным, DEBIT — красноватый фон и «-» красным.
              const credit = t.operation === 'CREDIT';
              const debit = t.operation === 'DEBIT';
              const amount = Math.abs(Number(t.amount));
              const sign = credit ? '+' : debit ? '-' : '';
              return (
                  <tr
                      key={t.id ?? `${t.clientId}-${t.createdAt}-${i}`}
                      className={`no-hover${credit ? ' tx-credit' : ''}${debit ? ' tx-debit' : ''}`}
                  >
                    {/* ID — сокращённо, полный в подсказке, копируется по клику (как в «Ордерах») */}
                    <td className="c-id"><CopyValue value={t.id} onCopy={copy} /></td>
                    <td>
                      <span className="cell-main">{t.clientUsername || '—'}</span>
                      <span className="cell-sub"><CopyValue value={t.clientId} onCopy={copy} /></span>
                    </td>
                    <td className="mono nowrap">{fmtDateTime(t.createdAt)}</td>
                    <td className="nowrap">
                      <span className={`op-dot${credit ? ' op-credit' : ''}${debit ? ' op-debit' : ''}`} />
                      {opLabel(t.operation)}
                    </td>
                    <td className={`c-right mono strong nowrap${credit ? ' amount-plus' : ''}${debit ? ' amount-minus' : ''}`}>
                      {Number.isFinite(amount) ? `${sign}${fmtAmount(amount)}` : '—'}
                    </td>
                    <td>
                      <span className={`badge badge-${TX_TYPE_MOD[t.type] || 'neutral'}`}>
                        {typeLabel(t.type)}
                      </span>
                    </td>
                    <td className="tx-comment">{t.comment || '-'}</td>
                  </tr>
              );
            })}
            </tbody>
          </table>

          {!loading && rows.length === 0 && (
              <div className="table-empty">Транзакций не найдено</div>
          )}
          {loading && (
              <div className="table-empty"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>
          )}
        </div>

        <Pagination
            page={page} total={total} busy={loading}
            onPage={setPage} label="Всего транзакций"
        />

        {creating && (
            <CreateTransactionDialog
                operations={opList}
                showToast={showToast}
                onCancel={() => setCreating(false)}
                onCreated={onCreated}
            />
        )}
      </>
  );
}

/* ---- Окно «Новая транзакция»: ручная корректировка баланса ---- */
function CreateTransactionDialog({ operations, showToast, onCancel, onCreated }) {
  const [operation, setOperation] = useState('CREDIT'); // по ТЗ по умолчанию «Зачисление»
  const [client, setClient] = useState(null);           // выбранный клиент { id, username }
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !saving) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, saving]);

  const clearError = (key) => setErrors((prev) => (prev[key] ? { ...prev, [key]: '' } : prev));

  const submit = async () => {
    const errs = {};
    if (!client) errs.client = 'Выберите клиента';
    const rawAmount = String(amount).trim();
    if (!/^\d+$/.test(rawAmount) || Number(rawAmount) <= 0) errs.amount = 'Укажите целое число больше нуля';
    if (!comment.trim()) errs.comment = 'Укажите причину корректировки';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setSaving(true);
    try {
      await api.createTransaction({
        clientId: client.id,
        operation,
        amount: Number(rawAmount),
        comment: comment.trim(),
      });
      onCreated();
    } catch (e) {
      showToast(e.message || 'Не удалось создать транзакцию', 'error');
      setSaving(false);
    }
  };

  return (
      <div className="overlay">
        <div className="modal">
          <div className="ocard-head">
            <span className="ocard-ico"><i className="fa-solid fa-plus" /></span>
            <div className="ocard-title">
              <span className="ocard-name">Новая транзакция</span>
              <span className="ocard-sub">Ручная корректировка баланса</span>
            </div>
            <button type="button" className="close-x" onClick={onCancel} disabled={saving} aria-label="Закрыть">×</button>
          </div>

          <div className="modal-body tx-form">
            <FieldRow label="Операция">
              <select value={operation} disabled={saving} onChange={(e) => setOperation(e.target.value)}>
                {operations.map((o) => (
                    <option key={o.name} value={o.name}>{o.description || o.name}</option>
                ))}
              </select>
            </FieldRow>

            <FieldRow label="Клиент" error={errors.client}>
              <ClientSuggest
                  value={client}
                  disabled={saving}
                  invalid={Boolean(errors.client)}
                  onChange={(c) => { setClient(c); clearError('client'); }}
              />
            </FieldRow>

            <FieldRow label="Сумма, ₽" error={errors.amount}>
              <input
                  type="text" inputMode="numeric" placeholder="0"
                  value={amount} disabled={saving}
                  className={errors.amount ? 'invalid' : ''}
                  onChange={(e) => { setAmount(e.target.value); clearError('amount'); }}
              />
            </FieldRow>
            {!errors.amount && <span className="field-hint">Только целое число больше нуля</span>}

            <FieldRow label="Причина корректировки" error={errors.comment}>
              <textarea
                  rows={3}
                  placeholder="Например: возврат после сбоя при подтверждении ордера"
                  value={comment} disabled={saving}
                  className={errors.comment ? 'invalid' : ''}
                  onChange={(e) => { setComment(e.target.value); clearError('comment'); }}
              />
            </FieldRow>
          </div>

          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>Отмена</button>
            <button type="button" className="btn btn-primary" onClick={submit} disabled={saving}>Создать</button>
          </div>
        </div>
      </div>
  );
}

/* ---- Поиск клиента с подсказкой: логин или ID, выбор из списка ----
   Бэк ищет по логину и по ID отдельными параметрами, поэтому, как и
   в «Конфигурации мерчантов», шлём два запроса и объединяем ответы.
   Если после выбора текст в поле изменили — выбор сбрасывается. */
function ClientSuggest({ value, disabled, invalid, onChange }) {
  const [q, setQ] = useState(value?.username || '');
  const [found, setFound] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const term = q.trim();
    // Уже выбранного клиента не ищем повторно.
    if (term.length < 2 || (value && term === value.username)) {
      setFound([]);
      return undefined;
    }
    let alive = true;
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const [byName, byId] = await Promise.all([
          api.clients({ username: term, page: 0, size: 10 }).catch(() => ({ items: [] })),
          isUuid(term) ? api.clients({ id: term, page: 0, size: 10 }).catch(() => ({ items: [] })) : { items: [] },
        ]);
        const map = new Map();
        [...byName.items, ...byId.items].forEach((c) => map.set(c.id, c));
        if (alive) { setFound([...map.values()]); setOpen(true); }
      } finally {
        if (alive) setLoading(false);
      }
    }, 350); // не дёргаем бэк на каждую букву
    return () => { alive = false; clearTimeout(t); };
  }, [q, value]);

  // Клик вне поля закрывает список.
  useEffect(() => {
    if (!open) return undefined;
    const onDocDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', onDocDown);
    return () => document.removeEventListener('mousedown', onDocDown);
  }, [open]);

  const pick = (c) => {
    onChange({ id: c.id, username: c.username });
    setQ(c.username || c.id);
    setFound([]);
    setOpen(false);
  };

  return (
      <div className="suggest" ref={ref}>
        <input
            type="text" placeholder="ID или логин клиента"
            value={q} disabled={disabled}
            className={invalid ? 'invalid' : ''}
            onChange={(e) => {
              setQ(e.target.value);
              if (value) onChange(null);
            }}
            onFocus={() => { if (found.length) setOpen(true); }}
            onKeyDown={(e) => { if (e.key === 'Escape' && open) { e.stopPropagation(); setOpen(false); } }}
        />
        {open && q.trim().length >= 2 && !value && (
            <ul className="suggest-list">
              {loading && found.length === 0 && <li className="suggest-note">Поиск…</li>}
              {!loading && found.length === 0 && <li className="suggest-note">Клиенты не найдены</li>}
              {found.map((c) => (
                  <li key={c.id}>
                    <button type="button" onClick={() => pick(c)}>
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