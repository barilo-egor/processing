import {useCallback, useEffect, useRef, useState} from 'react';
import {api, dateToMs, fmtDateTime, fmtPercent, isUuid,} from './api.js';

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
  { id: 'merchants', title: 'Конфигурация мерчантов', icon: 'fa-solid fa-sliders' },
];

const FALLBACK_STATUSES = [
  { name: 'ACTIVE', description: 'Активен' },
  { name: 'BLOCKED', description: 'Заблокирован' },
];

export default function App() {
  const [section, setSection] = useState('clients');
  const [toast, setToast] = useState(null);
  const [statuses, setStatuses] = useState(FALLBACK_STATUSES);
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

  // Справочник статусов — из словаря бэка, с запасным вариантом.
  useEffect(() => {
    api.dictionary()
      .then((d) => {
        const list = d?.ClientStatus;
        if (Array.isArray(list) && list.length) setStatuses(list);
      })
      .catch(() => { /* останется запасной список */ });
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
        <header className="topbar"><h1>{current.title}</h1></header>
        <main className="content">
          {section === 'clients' && (
            <ClientsSection
              statuses={statuses}
              showToast={showToast}
              onOpenMerchantConfig={openMerchantConfig}
            />
          )}
          {section === 'merchants' && (
            <MerchantsSection
              client={configClient}
              statuses={statuses}
              showToast={showToast}
              onPickClient={setConfigClient}
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
  id: '', username: '', status: '',
  dateMode: 'equal', dateEqual: '', dateFrom: '', dateTo: '',
};

function ClientsSection({ statuses, showToast, onOpenMerchantConfig }) {
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
      if (f.status) params.status = f.status;
      // Даты уходят как UNIX-время в миллисекундах.
      if (f.dateMode === 'equal') {
        if (f.dateEqual) {
          params.from = dateToMs(f.dateEqual, 'start');
          params.to = dateToMs(f.dateEqual, 'end');
        }
      } else {
        if (f.dateFrom) params.from = dateToMs(f.dateFrom, 'start');
        if (f.dateTo) params.to = dateToMs(f.dateTo, 'end');
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

          <div className="field">
            <label htmlFor="f-status">Статус</label>
            <select id="f-status" value={draft.status} onChange={(e) => setF('status', e.target.value)}>
              <option value="">Все</option>
              {statuses.map((s) => (
                <option key={s.name} value={s.name}>{s.description}</option>
              ))}
            </select>
          </div>

          {/* Режим и сами даты — в одном блоке, чтобы сетка не разносила их по строкам */}
          <div className="field-pair">
            <div className="field">
              <label htmlFor="f-mode">Дата регистрации</label>
              <select id="f-mode" value={draft.dateMode} onChange={(e) => setF('dateMode', e.target.value)}>
                <option value="equal">Равна</option>
                <option value="range">Диапазон</option>
              </select>
            </div>
            {draft.dateMode === 'equal' ? (
              <div className="field">
                <label htmlFor="f-date">Дата</label>
                <input id="f-date" type="date" value={draft.dateEqual}
                  onChange={(e) => setF('dateEqual', e.target.value)} />
              </div>
            ) : (
              <div className="field">
                <label>С / по</label>
                <div className="range-row">
                  <input type="date" value={draft.dateFrom} onChange={(e) => setF('dateFrom', e.target.value)} />
                  <span className="range-dash">—</span>
                  <input type="date" value={draft.dateTo} onChange={(e) => setF('dateTo', e.target.value)} />
                </div>
              </div>
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
                  <th>Статус</th>
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
                    <td><StatusBadge status={c.status} statuses={statuses} /></td>
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
          statuses={statuses}
          onClose={() => setCard(null)}
          onUpdated={applyUpdated}
          onCopy={copy}
          showToast={showToast}
        />
      )}
    </>
  );
}

/* ---- Бейдж статуса ---- */
function StatusBadge({ status, statuses }) {
  const label = statuses.find((s) => s.name === status)?.description || status || '—';
  const mod = status === 'ACTIVE' ? 'ok' : status === 'BLOCKED' ? 'bad' : 'neutral';
  return <span className={`badge badge-${mod}`}>{label}</span>;
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

/* ==================== Карточка клиента ==================== */

function ClientCard({ client, statuses, onClose, onUpdated, onCopy, showToast }) {
  // Какая строка сейчас редактируется: 'status' | 'commission' | 'timeout' | null
  const [editing, setEditing] = useState(null);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirm, setConfirm] = useState(null); // подтверждение смены статуса
  const inputRef = useRef(null);

  useEffect(() => {
    if (editing && inputRef.current) inputRef.current.focus();
  }, [editing]);

  const startEdit = (row) => {
    setError('');
    setEditing(row);
    if (row === 'status') setValue(client.status ?? '');
    if (row === 'commission') setValue(client.commissionPercent ?? '');
    if (row === 'timeout') setValue(client.orderTimeoutSeconds ?? '');
  };

  const cancel = () => { setEditing(null); setError(''); };

  const onKeyDown = (e) => {
    if (e.key === 'Escape') { e.preventDefault(); cancel(); }
    if (e.key === 'Enter' && editing !== 'status') { e.preventDefault(); save(); }
  };

  // Отправляем ТОЛЬКО изменённое поле: бэк обновляет всё, что не null.
  const patch = async (body, successText) => {
    setSaving(true);
    try {
      const updated = await api.updateClient(client.id, body);
      onUpdated(updated);
      setEditing(null);
      setError('');
      showToast(successText, 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось сохранить', 'error');
    } finally {
      setSaving(false);
    }
  };

  const save = () => {
    if (editing === 'commission') {
      const raw = String(value).replace(',', '.').trim();
      const n = Number(raw);
      if (raw === '' || !Number.isFinite(n) || n < 0) {
        setError('Укажите число от 0');
        return;
      }
      const rounded = Math.round(n * 10) / 10; // точность до десятых
      if (rounded === Number(client.commissionPercent)) {
        showToast('Изменений нет', 'info');
        setEditing(null);
        return;
      }
      patch({ commissionPercent: rounded }, 'Изменения сохранены');
      return;
    }

    if (editing === 'timeout') {
      const n = Number(String(value).trim());
      if (!Number.isInteger(n) || n < TIMEOUT_MIN || n > TIMEOUT_MAX) {
        setError(`Укажите целое число от ${TIMEOUT_MIN} до ${TIMEOUT_MAX}`);
        return;
      }
      if (n === Number(client.orderTimeoutSeconds)) {
        showToast('Изменений нет', 'info');
        setEditing(null);
        return;
      }
      patch({ orderTimeoutSeconds: n }, 'Изменения сохранены');
      return;
    }

    if (editing === 'status') {
      if (value === client.status) {
        showToast('Изменений нет', 'info');
        setEditing(null);
        return;
      }
      setConfirm(value); // статус меняется только после подтверждения
    }
  };

  const applyStatus = () => {
    const next = confirm;
    const text = next === 'BLOCKED' ? 'Клиент заблокирован'
      : next === 'ACTIVE' ? 'Клиент разблокирован'
        : 'Статус изменён';
    setConfirm(null);
    patch({ status: next }, text);
  };

  const statusLabel = (name) =>
    statuses.find((s) => s.name === name)?.description || name;

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <div className="modal-head">
          <h2>Клиент</h2>
          <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
        </div>

        <div className="modal-body">
          <Row label="ID">
            <span className="copyable" onClick={() => onCopy(client.id)} title="Скопировать">
              <span className="mono">{client.id}</span> <i className="fa-regular fa-copy" />
            </span>
          </Row>

          <Row label="Логин">
            <span className="copyable" onClick={() => onCopy(client.username)} title="Скопировать">
              {client.username || '—'} <i className="fa-regular fa-copy" />
            </span>
          </Row>

          {/* --- Статус --- */}
          <EditableRow
            label="Статус"
            editing={editing === 'status'}
            saving={saving}
            error={editing === 'status' ? error : ''}
            onEdit={() => startEdit('status')}
            onSave={save}
            onCancel={cancel}
            view={<StatusBadge status={client.status} statuses={statuses} />}
          >
            <select ref={inputRef} value={value} onChange={(e) => setValue(e.target.value)} onKeyDown={onKeyDown}>
              {statuses.map((s) => (
                <option key={s.name} value={s.name}>{s.description}</option>
              ))}
            </select>
          </EditableRow>

          <Row label="Дата регистрации">
            <span className="mono">{fmtDateTime(client.registeredAt)}</span>
          </Row>

          {/* --- Комиссия --- */}
          <EditableRow
            label="Комиссия"
            editing={editing === 'commission'}
            saving={saving}
            error={editing === 'commission' ? error : ''}
            onEdit={() => startEdit('commission')}
            onSave={save}
            onCancel={cancel}
            view={<span className="mono">{fmtPercent(client.commissionPercent)}</span>}
          >
            <div className="input-suffix">
              <input ref={inputRef} type="number" step="0.1" min="0"
                value={value} onChange={(e) => setValue(e.target.value)} onKeyDown={onKeyDown} />
              <span className="suffix">%</span>
            </div>
          </EditableRow>

          {/* --- Время активности ордеров --- */}
          <EditableRow
            label="Время активности ордеров"
            editing={editing === 'timeout'}
            saving={saving}
            error={editing === 'timeout' ? error : ''}
            onEdit={() => startEdit('timeout')}
            onSave={save}
            onCancel={cancel}
            view={<span className="mono">{client.orderTimeoutSeconds ?? '—'} сек</span>}
          >
            <div className="input-suffix">
              <input ref={inputRef} type="number" step="1" min={TIMEOUT_MIN} max={TIMEOUT_MAX}
                value={value} onChange={(e) => setValue(e.target.value)} onKeyDown={onKeyDown} />
              <span className="suffix">сек</span>
            </div>
          </EditableRow>
        </div>

        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Закрыть</button>
        </div>
      </div>

      {confirm && (
        <ConfirmStatus
          username={client.username}
          next={confirm}
          nextLabel={statusLabel(confirm)}
          onCancel={() => setConfirm(null)}
          onApply={applyStatus}
        />
      )}
    </div>
  );
}

/* ---- Обычная строка карточки ---- */
function Row({ label, children }) {
  return (
    <div className="prow">
      <span className="k">{label}</span>
      <span className="v">{children}</span>
    </div>
  );
}

/* ---- Строка с редактированием по месту ----
   Обычный вид: значение + карандаш при наведении.
   Режим правки: поле ввода, галочка и крестик, Escape отменяет. */
function EditableRow({ label, editing, saving, error, onEdit, onSave, onCancel, view, children }) {
  return (
    <div className={`prow prow-editable${editing ? ' is-editing' : ''}`}>
      <span className="k">{label}</span>
      <span className="v">
        {editing ? (
          <span className="edit-wrap">
            {children}
            <button type="button" className="icon-btn ok" onClick={onSave} disabled={saving} title="Сохранить">
              <i className="fa-solid fa-check" />
            </button>
            <button type="button" className="icon-btn no" onClick={onCancel} disabled={saving} title="Отмена">
              <i className="fa-solid fa-xmark" />
            </button>
          </span>
        ) : (
          <>
            {view}
            <button type="button" className="icon-btn pencil" onClick={onEdit} title="Изменить">
              <i className="fa-solid fa-pencil" />
            </button>
          </>
        )}
      </span>
      {error && <span className="row-error">{error}</span>}
    </div>
  );
}

/* ---- Подтверждение смены статуса ---- */
function ConfirmStatus({ username, next, nextLabel, onCancel, onApply }) {
  let text; let action;
  if (next === 'BLOCKED') {
    text = `Заблокировать клиента ${username}? Клиент потеряет доступ к API — его запросы будут отклоняться.`;
    action = 'Заблокировать';
  } else if (next === 'ACTIVE') {
    text = `Разблокировать клиента ${username}? Клиенту вернётся доступ к API, его запросы снова будут приниматься.`;
    action = 'Разблокировать';
  } else {
    text = `Изменить статус клиента ${username} на «${nextLabel}»?`;
    action = 'Изменить';
  }

  return (
    <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && onCancel()}>
      <div className="modal modal-sm">
        <div className="modal-head">
          <h2>Смена статуса</h2>
          <button type="button" className="close-x" onClick={onCancel} aria-label="Закрыть">×</button>
        </div>
        <div className="modal-body"><p className="confirm-text">{text}</p></div>
        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>Отмена</button>
          <button
            type="button"
            className={`btn ${next === 'BLOCKED' ? 'btn-danger' : 'btn-primary'}`}
            onClick={onApply}
          >
            {action}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ==================== Конфигурация мерчантов ==================== */
/* Очерёдность (колонка «№», перетаскивание) пока не реализована:
   бэк не отдаёт позицию мерчанта и не принимает её в PATCH.
   Всё остальное из ТЗ работает: включение, суммы, поиск.
   Пагинации по 25 тоже нет — эндпоинт отдаёт плоский массив без страниц. */

function MerchantsSection({ client, statuses, showToast, onPickClient }) {
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
        <ClientPicker showToast={showToast} statuses={statuses} onPick={onPickClient} />
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
function ClientPicker({ showToast, statuses, onPick }) {
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
                <StatusBadge status={c.status} statuses={statuses} />
                <span className="pl-id mono">{c.id}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
