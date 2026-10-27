import {useCallback, useEffect, useRef, useState} from 'react';
import {
  api,
  dateFilterParams,
  dictItems,
  dictLabel,
  EMPTY_DATE_FILTER,
  formatDateTime,
  formatNumber,
  formatRate,
  isPositiveInt,
  isValidCallbackUrl,
  isValidWalletAddress,
  logout,
  PAGE_SIZE,
  shorten,
} from './api.js';
// Общие для обоих кабинетов компоненты — frontend/shared.
import ConfirmDialog from '@shared/ConfirmDialog.jsx';
import {DocPage, DocsNav, docIdOf, sectionFromUrl, syncDocUrl} from '@shared/docs.jsx';
import ThemeToggle from '@shared/ThemeToggle.jsx';

/* ============================================================
   Личный кабинет клиента API.

   Разделы:
   - «Профиль» — два независимых блока «Callback URL» и «API-токены»,
     каждый сохраняется отдельно;
   - «Ордера», «Транзакции», «Заявки на вывод» — таблицы с фильтром,
     пагинацией по 25 и сортировкой от новых к старым. Значения
     перечислений берутся из словаря.

   Регистрации в интерфейсе нет: учётные записи заводит
   администратор в кабинете администратора API.
   ============================================================ */

// Больше десяти активных токенов у клиента быть не может.
const TOKENS_LIMIT = 10;

// Не больше 10 адресов в белом списке IP (ТЗ; бэк проверяет так же).
const IP_LIMIT = 10;

// Ключ в localStorage: свёрнуто ли боковое меню на широком экране.
const SIDEBAR_KEY = 'client.sidebarCollapsed';

function readCollapsed() {
  try { return localStorage.getItem(SIDEBAR_KEY) === '1'; } catch { return false; }
}

// «Заявки на вывод» — сразу под «Ордера», как в кабинете администратора.
const SECTIONS = [
  { id: 'profile', title: 'Профиль', icon: 'fa-solid fa-user-gear' },
  { id: 'orders', title: 'Ордера', icon: 'fa-solid fa-receipt' },
  { id: 'withdrawals', title: 'Заявки на вывод', icon: 'fa-solid fa-money-bill-transfer' },
  { id: 'transactions', title: 'Транзакции', icon: 'fa-solid fa-right-left' },
];

export default function App() {
  // По умолчанию открываются «Ордера» — основной раздел для клиента.
  const [section, setSection] = useState(() => sectionFromUrl('orders'));
  // Меню на узком экране (≤720px) — выезжающая панель по кнопке ☰.
  const [menuOpen, setMenuOpen] = useState(false);
  // Меню на широком экране можно свернуть до полоски с иконками; выбор запоминается.
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [toast, setToast] = useState(null);
  // Страницы документации [{ id, title }]; пусто — пункта «Документация» в меню нет.
  const [docs, setDocs] = useState([]);
  // Профиль грузим один раз: логин нужен в шапке, id — для сохранения
  // Callback URL.
  const [profile, setProfile] = useState(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  // Словарь перечислений — один на все разделы.
  const [dict, setDict] = useState({});

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type, key: Date.now() });
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

  // Баланс в шапке берётся из того же профиля. Перечитываем его при каждой
  // смене раздела: баланс меняется после ордеров и заявок на вывод, а
  // кабинет может быть открыт долго. Ошибку фонового обновления не показываем.
  useEffect(() => {
    if (loadingProfile) return;
    api.profile().then((p) => setProfile(p)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section]);

  useEffect(() => {
    let alive = true;
    api.profile()
        .then((p) => { if (alive) setProfile(p); })
        .catch((e) => {
          if (alive) showToast(e.message || 'Не удалось загрузить профиль', 'error');
        })
        .finally(() => { if (alive) setLoadingProfile(false); });
    return () => { alive = false; };
  }, [showToast]);

  // Без словаря таблицы всё равно работают — вместо подписей
  // покажутся коды значений, поэтому ошибку не показываем.
  useEffect(() => {
    let alive = true;
    api.dictionary()
        .then((d) => { if (alive) setDict(d); })
        .catch(() => {});
    return () => { alive = false; };
  }, []);

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
      : SECTIONS.find((s) => s.id === section);

  return (
      <div className="layout">
        <aside
            className={`sidebar${menuOpen ? ' open' : ''}${collapsed ? ' collapsed' : ''}`}
            aria-label="Разделы"
        >
          <div className="brand">
            <span className="brand-ico"><i className="fa-solid fa-key" /></span>
            <span className="brand-name">Кабинет клиента</span>
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
            {SECTIONS.map((s) => (
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
            ))}
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
              <BalanceBadge value={profile?.balance} loading={loadingProfile && !profile} />
              <ThemeToggle />
              <AccountMenu username={profile?.username} />
            </div>
          </header>
          <main className="content">
            {section === 'profile' && (
                <>
                  <CallbackUrlBlock
                      profile={profile}
                      loading={loadingProfile}
                      showToast={showToast}
                  />
                  <ApiKeysBlock showToast={showToast} />
                  <IpWhitelistBlock showToast={showToast} />
                </>
            )}
            {section === 'orders' && (
                <OrdersSection dict={dict} showToast={showToast} timeoutSec={profile?.orderTimeoutSeconds} />
            )}
            {section === 'transactions' && <TransactionsSection dict={dict} showToast={showToast} />}
            {section === 'withdrawals' && <WithdrawalsSection dict={dict} showToast={showToast} />}
            {docId && <DocPage key={docId} id={docId} load={api.doc} />}
          </main>
        </div>

        {toast && <div className={`toast toast-${toast.type}`}>{toast.message}</div>}
      </div>
  );
}


/* ==================== Документация ====================
   Пункт меню, страница и адрес ?doc={id} — общие для обоих кабинетов:
   frontend/shared/docs.jsx. */

/* ==================== Меню аккаунта ==================== */
/* Логин клиента в правом углу верхней панели. Клик раскрывает меню
   с выходом; повторный клик, клик вне меню и Escape закрывают. */
/* ---- Баланс клиента в шапке ----
   Тот же баланс, что в карточке клиента у администратора (поле balance
   профиля GET /api/client/profile). Формат по ТЗ: «125 001,00 ₽», ноль — «0,00 ₽». */
function fmtBalance(v) {
  const n = Number(v ?? 0);
  return `${(Number.isFinite(n) ? n : 0).toLocaleString('ru-RU', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} ₽`;
}

function BalanceBadge({ value, loading }) {
  return (
      <div className="balance" title="Баланс">
        <span className="balance-label">Баланс</span>
        <span className="balance-value">{loading ? '…' : fmtBalance(value)}</span>
      </div>
  );
}

function AccountMenu({ username }) {
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [open, setOpen] = useState(false);
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
          <i className="fa-solid fa-circle-user" />
          <span className="account-name">{username || 'Клиент'}</span>
          <i className={`fa-solid fa-chevron-${open ? 'up' : 'down'} account-caret`} />
        </button>

        {open && (
            <div className="account-menu" role="menu">
              {/* На телефоне логин в шапке скрыт (место занимает баланс) — показываем его здесь */}
              <div className="account-menu-head">{username || 'Клиент'}</div>
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
                <i className="fa-solid fa-arrow-right-from-bracket" />
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

/* ==================== Блок «Callback URL» ==================== */

function CallbackUrlBlock({ profile, loading, showToast }) {
  const [value, setValue] = useState('');
  const [saved, setSaved] = useState('');   // что реально лежит на сервере
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const clientId = profile?.id ?? null;

  useEffect(() => {
    const url = profile?.callbackUrl ?? '';
    setValue(url);
    setSaved(url);
  }, [profile]);

  const onChange = (v) => {
    setValue(v);
    if (error) setError('');
  };

  const save = async () => {
    const next = value.trim();

    if (!isValidCallbackUrl(next)) {
      setError('Укажите корректный URL');
      return;
    }
    if (next === saved) {
      showToast('Изменений нет', 'info');
      return;
    }
    if (!clientId) {
      showToast('Профиль не загружен', 'error');
      return;
    }

    setSaving(true);
    try {
      await api.saveCallbackUrl(next);
      setSaved(next);
      setValue(next);
      showToast('Callback URL сохранён', 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось сохранить', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
      <section className="block">
        <div className="block-aside">
          <h2 className="block-title">Callback URL</h2>
          <p className="block-note">
            Адрес, на который приходят уведомления о смене статуса ордера.
            Оставьте поле пустым, чтобы не получать уведомления.
          </p>
        </div>

        <div className="block-body">
          <div className="field">
            <label htmlFor="callback-url">Адрес</label>
            <input
                id="callback-url"
                type="url"
                inputMode="url"
                placeholder="https://example.com/callback"
                value={value}
                disabled={loading || saving}
                className={error ? 'invalid' : ''}
                onChange={(e) => onChange(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') save(); }}
            />
            {error && <span className="field-error">{error}</span>}
          </div>

          <div className="block-actions">
            <button
                type="button"
                className="btn btn-primary"
                disabled={loading || saving}
                onClick={save}
            >
              <i className="fa-solid fa-floppy-disk" />
              Сохранить
            </button>
          </div>
        </div>
      </section>
  );
}

/* ==================== Блок «API-токены» ==================== */

function ApiKeysBlock({ showToast }) {
  const [keys, setKeys] = useState([]);
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [issued, setIssued] = useState(null);    // {name, token} — окно выдачи
  const [toDelete, setToDelete] = useState(null); // токен в подтверждении

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setKeys(await api.apiKeys());
    } catch (e) {
      showToast(e.message || 'Не удалось загрузить токены', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(); }, [load]);

  const limitReached = keys.length >= TOKENS_LIMIT;

  const create = async () => {
    const next = name.trim();

    if (!next) {
      setNameError('Укажите имя токена');
      return;
    }
    // Имя уникально среди токенов клиента — проверяем и на фронте,
    // чтобы не гонять заведомо неудачный запрос.
    if (keys.some((k) => k.name === next)) {
      setNameError('Токен с таким именем уже существует');
      return;
    }

    setCreating(true);
    try {
      const token = await api.createApiKey(next);
      setIssued({ name: next, token: String(token || '').trim() });
      setName('');
      setNameError('');
      await load();
    } catch (e) {
      showToast(e.message || 'Не удалось создать токен', 'error');
    } finally {
      setCreating(false);
    }
  };

  const remove = async (row) => {
    try {
      await api.deleteApiKey(row.id);
      setKeys((prev) => prev.filter((k) => k.id !== row.id));
      showToast('Токен удалён', 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось удалить токен', 'error');
    } finally {
      setToDelete(null);
    }
  };

  return (
      <section className="block">
        <div className="block-aside">
          <h2 className="block-title">API-токены</h2>
          <p className="block-note">
            Токен подставляется в запросы к API. Значение показывается один раз
            при создании — сохраните его в надёжном месте.
          </p>
        </div>

        <div className="block-body">
          <div className="create-row">
            <div className="field">
              <label htmlFor="token-name">Имя токена</label>
              <input
                  id="token-name"
                  type="text"
                  placeholder="Например, тестовый"
                  value={name}
                  disabled={creating || limitReached}
                  className={nameError ? 'invalid' : ''}
                  onChange={(e) => { setName(e.target.value); if (nameError) setNameError(''); }}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !limitReached) create(); }}
              />
              {nameError && <span className="field-error">{nameError}</span>}
            </div>

            <button
                type="button"
                className="btn btn-primary create-btn"
                disabled={creating || limitReached}
                onClick={create}
            >
              <i className="fa-solid fa-plus" />
              Создать
            </button>

            {limitReached && (
                <span className="limit-note">
              Достигнут лимит в {TOKENS_LIMIT} токенов. Удалите ненужный, чтобы создать новый.
            </span>
            )}
          </div>

          {loading ? (
              <div className="state"><i className="fa-solid fa-spinner fa-spin" />Загрузка…</div>
          ) : keys.length === 0 ? (
              <div className="state">
                <i className="fa-solid fa-key" />
                Токенов пока нет. Создайте первый, чтобы обращаться к API.
              </div>
          ) : (
              <div className="table-wrap">
                <table className="grid grid-tokens">
                  <thead>
                  <tr>
                    <th>Название</th>
                    <th>Превью</th>
                    <th className="c-act" aria-label="Действия" />
                  </tr>
                  </thead>
                  <tbody>
                  {keys.map((k) => (
                      <tr key={k.id} className="no-hover">
                        <td>{k.name}</td>
                        <td className="mono">{k.preview || '—'}</td>
                        <td className="c-act">
                          <button
                              type="button"
                              className="icon-btn no"
                              title="Удалить токен"
                              aria-label={`Удалить токен ${k.name}`}
                              onClick={() => setToDelete(k)}
                          >
                            <i className="fa-solid fa-trash" />
                          </button>
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
          )}
        </div>

        {issued && <IssuedTokenDialog data={issued} onClose={() => setIssued(null)} showToast={showToast} />}
        {toDelete && (
            <DeleteTokenDialog
                row={toDelete}
                onCancel={() => setToDelete(null)}
                onApply={() => remove(toDelete)}
            />
        )}
      </section>
  );
}

/* Окно выдачи нового токена: значение видно один раз. */
function IssuedTokenDialog({ data, onClose, showToast }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(data.token);
      showToast('Токен скопирован', 'success');
    } catch {
      showToast('Не удалось скопировать', 'error');
    }
  };

  return (
      <div className="overlay">
        <div className="modal">
          <div className="modal-head">
            <h2>Новый API-токен</h2>
            <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
          </div>

          <div className="modal-body">
            <p className="warn-text">
              <i className="fa-solid fa-triangle-exclamation" />
              Токен показывается первый и последний раз. Скопируйте и сохраните
              его — восстановить значение будет невозможно.
            </p>

            <div className="prow">
              <span className="k">Название</span>
              <span className="v">{data.name}</span>
            </div>

            <div className="field token-field">
              <label htmlFor="issued-token">Токен</label>
              <textarea id="issued-token" className="token-value" readOnly rows={3} value={data.token} />
            </div>

            <button type="button" className="btn btn-secondary" onClick={copy}>
              <i className="fa-solid fa-copy" />
              Скопировать
            </button>

            <p className="block-note">
              Если вы его потеряете, создайте новый.
            </p>
          </div>

          <div className="modal-foot">
            <button type="button" className="btn btn-primary" onClick={onClose}>Закрыть</button>
          </div>
        </div>
      </div>
  );
}

/* Подтверждение удаления токена. */
function DeleteTokenDialog({ row, onCancel, onApply }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && onCancel()}>
        <div className="modal modal-sm">
          <div className="modal-head">
            <h2>Удаление токена</h2>
            <button type="button" className="close-x" onClick={onCancel} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="confirm-text">
              Удалить токен «{row.name}»? Запросы к API с этим токеном перестанут
              приниматься. Действие необратимо.
            </p>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel}>Отмена</button>
            <button type="button" className="btn btn-danger" onClick={onApply}>Удалить</button>
          </div>
        </div>
      </div>
  );
}

/* ==================== Блок «Белый список IP» ====================
   По образцу «API-токенов»: поле «IP-адрес», «Добавить» и таблица адресов
   с удалением в строке. Названий у записей нет — адрес целиком.
   Не больше 10 адресов: на лимите «Добавить» неактивна и выводится
   предупреждение. Адрес проверяет бэк (IPv4 или IPv6) и сохраняет
   в нормализованном виде (::1 → 0:0:0:0:0:0:0:1), поэтому в таблицу
   кладём адрес из ответа, а не набранный. Ошибку 400 (неверный адрес,
   уже добавлен) показываем под полем. Удаление — с подтверждением.

   GET    /api/client/ip-address          -> [{ id, address }] в порядке добавления
   POST   /api/client/ip-address?address= -> { id, address }
   DELETE /api/client/ip-address/{id} */

function IpWhitelistBlock({ showToast }) {
  const [rows, setRows] = useState([]);
  const [address, setAddress] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [toDelete, setToDelete] = useState(null); // адрес в подтверждении
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await api.ipAddresses());
    } catch (e) {
      showToast(e.message || 'Не удалось загрузить белый список IP', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { load(); }, [load]);

  const limitReached = rows.length >= IP_LIMIT;

  const add = async () => {
    const next = address.trim();
    if (!next) {
      setError('Укажите IP-адрес');
      return;
    }
    setAdding(true);
    try {
      const row = await api.addIpAddress(next);
      if (row && row.id != null) setRows((prev) => [...prev, row]);
      else await load();
      setAddress('');
      setError('');
      showToast('IP-адрес добавлен', 'success');
    } catch (e) {
      if (e.status === 400) setError(e.message || 'Не удалось добавить IP-адрес');
      else showToast(e.message || 'Не удалось добавить IP-адрес', 'error');
    } finally {
      setAdding(false);
    }
  };

  const remove = async () => {
    setDeleting(true);
    try {
      await api.deleteIpAddress(toDelete.id);
      setRows((prev) => prev.filter((r) => r.id !== toDelete.id));
      showToast('IP-адрес удалён', 'success');
    } catch (e) {
      showToast(e.message || 'Не удалось удалить IP-адрес', 'error');
      load();
    } finally {
      setDeleting(false);
      setToDelete(null);
    }
  };

  return (
      <section className="block">
        <div className="block-aside">
          <h2 className="block-title">
            Белый список IP
            {!loading && <span className="block-count">{rows.length} из {IP_LIMIT}</span>}
          </h2>
          <p className="block-note">
            IP-адреса, с которых вы обращаетесь к API. Поддерживаются IPv4 и IPv6,
            в списке — не больше {IP_LIMIT} адресов.
          </p>
        </div>

        <div className="block-body">
          {limitReached && (
              <p className="limit-box">
                Достигнут лимит в {IP_LIMIT} адресов. Удалите ненужный, чтобы добавить новый.
              </p>
          )}

          <div className="create-row">
            <div className="field">
              <label htmlFor="ip-address">IP-адрес</label>
              <input
                  id="ip-address"
                  type="text"
                  inputMode="url"
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="203.0.113.17"
                  value={address}
                  disabled={adding || limitReached}
                  className={error ? 'invalid' : ''}
                  onChange={(e) => { setAddress(e.target.value); if (error) setError(''); }}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !limitReached) add(); }}
              />
              {error && <span className="field-error">{error}</span>}
            </div>

            <button
                type="button"
                className="btn btn-primary create-btn"
                disabled={adding || limitReached || loading}
                onClick={add}
            >
              {adding ? <i className="fa-solid fa-spinner fa-spin" /> : <i className="fa-solid fa-plus" />}
              Добавить
            </button>
          </div>

          {loading ? (
              <div className="state"><i className="fa-solid fa-spinner fa-spin" />Загрузка…</div>
          ) : rows.length === 0 ? (
              <div className="state">
                <i className="fa-solid fa-shield-halved" />
                Адресов пока нет.
              </div>
          ) : (
              <div className="table-wrap">
                <table className="grid grid-tokens">
                  <thead>
                  <tr>
                    <th>IP-адрес</th>
                    <th className="c-act" aria-label="Действия" />
                  </tr>
                  </thead>
                  <tbody>
                  {rows.map((r) => (
                      <tr key={r.id} className="no-hover">
                        <td className="code ip-cell">{r.address}</td>
                        <td className="c-act">
                          <button
                              type="button"
                              className="icon-btn no"
                              title="Удалить адрес"
                              aria-label={`Удалить адрес ${r.address}`}
                              onClick={() => setToDelete(r)}
                          >
                            <i className="fa-solid fa-trash" />
                          </button>
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
          )}
        </div>

        {toDelete && (
            <ConfirmDialog
                title="Удаление IP-адреса"
                confirmLabel="Удалить"
                cancelLabel="Отмена"
                confirmClass="btn-danger"
                busy={deleting}
                onConfirm={remove}
                onCancel={() => setToDelete(null)}
            >
              Удалить адрес <b className="code">{toDelete.address}</b> из белого списка?
            </ConfirmDialog>
        )}
      </section>
  );
}

/* ==================== Общее для разделов-таблиц ==================== */

/* Список с серверной пагинацией.
   Фильтр применяется только по «Поиску» / «Сбросить»: в applied лежат
   параметры последнего поиска, черновик полей хранит сам раздел. */
function usePagedList(fetchPage, showToast, errorText) {
  const [applied, setApplied] = useState({});
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(null); // null — по умолчанию новые сверху
  const [data, setData] = useState({ rows: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    // Без выбранной сортировки параметр не шлём — api подставит «новые сверху».
    fetchPage({ ...applied, page, ...(sort ? { sort: sortParam(sort) } : {}) })
        .then((d) => { if (alive) setData(d); })
        .catch((e) => { if (alive) showToast(e.message || errorText, 'error'); })
        .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [fetchPage, applied, page, sort, reloadKey, showToast, errorText]);

  return {
    ...data,
    page,
    loading,
    setPage,
    sort,
    // Клик по заголовку: по возрастанию → по убыванию → по умолчанию; с первой страницы.
    onSort: (field) => { setSort((s) => nextSort(s, field)); setPage(0); },
    // Новый поиск — всегда с первой страницы.
    search: (params) => { setApplied(params); setPage(0); setReloadKey((k) => k + 1); },
    // Перечитать текущую страницу с тем же фильтром.
    reload: () => setReloadKey((k) => k + 1),
    // Перечитать с первой страницы (новая запись окажется сверху).
    reloadFirst: () => { setPage(0); setReloadKey((k) => k + 1); },
  };
}

/* ---- Сортировка по клику на заголовок ----
   Сортирует бэк (sort=поле,asc|desc), поэтому порядок общий для всей
   выборки с учётом фильтра, а не только для текущей страницы.
   Так же, как в кабинете администратора. */
function nextSort(sort, field) {
  if (!sort || sort.field !== field) return { field, dir: 'asc' };
  if (sort.dir === 'asc') return { field, dir: 'desc' };
  return null;
}

function sortParam(sort) {
  return sort ? `${sort.field},${sort.dir}` : undefined;
}

function SortTh({ field, list, className = '', children }) {
  const { sort, onSort } = list;
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

/* Копирование в буфер с тостом. */
async function copyText(text, showToast, okText) {
  try {
    await navigator.clipboard.writeText(String(text));
    showToast(okText, 'success');
  } catch {
    showToast('Не удалось скопировать', 'error');
  }
}

/* Сокращённое значение с кнопкой копирования, полное — в подсказке. */
function CopyCell({ value, head, tail, showToast, okText }) {
  if (!value) return <span className="dim">-</span>;
  return (
      <span className="copy-cell">
        <span className="code" title={value}>{shorten(value, head, tail)}</span>
        <button
            type="button"
            className="icon-btn icon-btn-sm"
            title="Скопировать"
            aria-label="Скопировать"
            onClick={() => copyText(value, showToast, okText)}
        >
          <i className="fa-regular fa-copy" />
        </button>
      </span>
  );
}

/* Цветная метка. tone — green | blue | cyan | amber | purple | red | brown | grey. */
function Badge({ tone = 'grey', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

/* Цвет статуса ордера — по макету ТЗ. Список статусов из словаря:
   NEW, SUCCESS, TIMEOUT, DISPUTE, CANCELED, SEARCHING («Поиск реквизитов»),
   DETAILS_NOT_FOUND («Реквизиты не найдены»); неизвестные — серые.
   Цвета новых статусов — как в документации. */
const ORDER_STATUS_TONE = {
  SEARCHING: 'cyan',
  DETAILS_NOT_FOUND: 'red',
  NEW: 'blue',
  SUCCESS: 'green',
  TIMEOUT: 'amber',
  DISPUTE: 'purple',
  CANCELED: 'grey',
};
const orderStatusTone = (status) => ORDER_STATUS_TONE[status] || 'grey';

const TX_TYPE_TONE = {
  ORDER_CONFIRMATION: 'green',
  CLIENT_WITHDRAWAL: 'red',
  MANUAL_CORRECT: 'brown',
};

const WITHDRAWAL_STATUS_TONE = {
  NEW: 'blue',
  APPROVED: 'green',
  CANCELED: 'red',
};

/* Выпадающий список фильтра; первый пункт — «Все». */
function SelectField({ id, label, value, items, onChange }) {
  return (
      <div className="field">
        <label htmlFor={id}>{label}</label>
        <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">Все</option>
          {items.map((x) => <option key={x.value} value={x.value}>{x.label}</option>)}
        </select>
      </div>
  );
}

/* Фильтр по дате создания: «Равна» — одна дата, «Диапазон» — две.
   Каждая дата — два поля, дата и время с секундами; время
   необязательно. */
function DateFilter({ idPrefix, value, onChange }) {
  const set = (patch) => onChange({ ...value, ...patch });

  const pair = (label, dateKey, timeKey) => (
      <div className="field">
        <label htmlFor={`${idPrefix}-${dateKey}`}>
          {label} <span className="label-note">· время необязательно</span>
        </label>
        <div className="date-pair">
          <input
              id={`${idPrefix}-${dateKey}`}
              type="date"
              value={value[dateKey]}
              onChange={(e) => set({ [dateKey]: e.target.value })}
          />
          <input
              type="time"
              step="1"
              aria-label={`${label}: время`}
              value={value[timeKey]}
              onChange={(e) => set({ [timeKey]: e.target.value })}
          />
        </div>
      </div>
  );

  // Режим и даты — одним блоком; «С» и «По» не разрываются переносом.
  return (
      <div className={`date-group${value.mode === 'range' ? ' date-group-range' : ''}`}>
        <div className="field">
          <label htmlFor={`${idPrefix}-mode`}>Дата создания</label>
          <select
              id={`${idPrefix}-mode`}
              value={value.mode}
              onChange={(e) => set({ mode: e.target.value })}
          >
            <option value="eq">Равна</option>
            <option value="range">Диапазон</option>
          </select>
        </div>
        <div className="date-fields">
          {value.mode === 'eq'
              ? pair('Дата', 'date', 'time')
              : (
                  <>
                    {pair('С', 'fromDate', 'fromTime')}
                    {pair('По', 'toDate', 'toTime')}
                  </>
              )}
        </div>
      </div>
  );
}

/* Панель фильтра: поля в строку, под ними «Поиск», «Сбросить» и «Обновить».
   «Обновить» перечитывает текущую страницу с уже применённым фильтром —
   стоит здесь, а не над таблицей, чтобы не занимать место.
   extra — кнопки справа (например, «Создать заявку»). */
function FilterPanel({ children, onSearch, onReset, onRefresh, refreshing, extra }) {
  // Enter в любом поле панели запускает поиск.
  const onKeyDown = (e) => {
    if (e.key === 'Enter' && e.target.tagName === 'INPUT') onSearch();
  };

  return (
      <section className="filters" onKeyDown={onKeyDown}>
        <div className="filters-row">{children}</div>
        <div className="filters-actions">
          <button type="button" className="btn btn-primary" onClick={onSearch}>
            <i className="fa-solid fa-magnifying-glass" />
            Поиск
          </button>
          <button type="button" className="btn btn-secondary" onClick={onReset}>
            Сбросить
          </button>
          {onRefresh && (
              <button type="button" className="btn btn-secondary" onClick={onRefresh} disabled={refreshing} title="Перечитать данные">
                <i className={`fa-solid fa-rotate-right${refreshing ? ' fa-spin' : ''}`} />
                Обновить
              </button>
          )}
          {extra && <div className="filters-extra">{extra}</div>}
        </div>
      </section>
  );
}

/* Карточка таблицы: сама таблица, заглушка при пустом списке,
   под таблицей — общее количество и пагинация. */
function TableCard({ list, emptyText, emptyIcon, totalLabel, children }) {
  const { rows, total, page, loading, setPage } = list;

  let body;
  if (loading && rows.length === 0) {
    body = <div className="state state-flat"><i className="fa-solid fa-spinner fa-spin" />Загрузка…</div>;
  } else if (rows.length === 0) {
    body = <div className="state state-flat"><i className={emptyIcon} />{emptyText}</div>;
  } else {
    body = <div className={`table-scroll${loading ? ' is-loading' : ''}`}>{children}</div>;
  }

  return (
      <section className="table-card">
        {body}
        {total > 0 && (
            <div className="table-foot">
              <span className="dim">{totalLabel}: {formatNumber(total, 0)}</span>
              <Pager page={page} total={total} onPage={setPage} />
            </div>
        )}
      </section>
  );
}

/* Пагинация: «в начало», «назад», номера страниц, «вперёд», «в конец».
   Номера — окно из пяти вокруг текущей. page — с нуля. */
function Pager({ page, total, onPage }) {
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const last = pages - 1;
  const from = Math.max(0, Math.min(page - 2, last - 4));
  const to = Math.min(last, from + 4);
  const nums = [];
  for (let i = from; i <= to; i += 1) nums.push(i);

  const go = (p) => { if (p >= 0 && p <= last && p !== page) onPage(p); };

  return (
      <nav className="pager" aria-label="Страницы">
        <button type="button" className="pager-btn" disabled={page === 0} onClick={() => go(0)} aria-label="Первая страница">«</button>
        <button type="button" className="pager-btn" disabled={page === 0} onClick={() => go(page - 1)} aria-label="Предыдущая страница">‹</button>
        {nums.map((p) => (
            <button
                key={p}
                type="button"
                className={`pager-btn${p === page ? ' active' : ''}`}
                aria-current={p === page ? 'page' : undefined}
                onClick={() => go(p)}
            >
              {p + 1}
            </button>
        ))}
        <button type="button" className="pager-btn" disabled={page === last} onClick={() => go(page + 1)} aria-label="Следующая страница">›</button>
        <button type="button" className="pager-btn" disabled={page === last} onClick={() => go(last)} aria-label="Последняя страница">»</button>
      </nav>
  );
}

/* ==================== Раздел «Ордера» ==================== */

const EMPTY_ORDERS_FILTER = { status: '', method: '' };

function OrdersSection({ dict, showToast, timeoutSec }) {
  const list = usePagedList(api.orders, showToast, 'Не удалось загрузить ордера');
  const [draft, setDraft] = useState(EMPTY_ORDERS_FILTER);
  const [card, setCard] = useState(null); // открытый ордер

  const onSearch = () => list.search({ status: draft.status, method: draft.method });
  const onReset = () => { setDraft(EMPTY_ORDERS_FILTER); list.search({}); };

  return (
      <>
        <FilterPanel onSearch={onSearch} onReset={onReset} onRefresh={list.reload} refreshing={list.loading}>
          <SelectField
              id="orders-status"
              label="Статус"
              value={draft.status}
              items={dictItems(dict, 'OrderStatus')}
              onChange={(v) => setDraft((d) => ({ ...d, status: v }))}
          />
          <SelectField
              id="orders-method"
              label="Способ оплаты"
              value={draft.method}
              items={dictItems(dict, 'RequestMethod')}
              onChange={(v) => setDraft((d) => ({ ...d, method: v }))}
          />
        </FilterPanel>

        <p className="table-hint">Двойной клик по строке — карточка ордера и управление статусом</p>

        <TableCard
            list={list}
            emptyText="Ордеров не найдено"
            emptyIcon="fa-solid fa-receipt"
            totalLabel="Всего ордеров"
        >
          <table className="grid grid-orders">
            <thead>
            <tr>
              <SortTh field="id" list={list}>ID ордера</SortTh>
              <SortTh field="createdAt" list={list}>Создан</SortTh>
              <SortTh field="internalId" list={list}>ID в вашей системе</SortTh>
              <SortTh field="status" list={list}>Статус</SortTh>
              <SortTh field="method" list={list}>Способ оплаты</SortTh>
              <SortTh field="amount" list={list} className="num">Сумма, ₽</SortTh>
              <SortTh field="callbackUrl" list={list}>Callback URL</SortTh>
            </tr>
            </thead>
            <tbody>
            {list.rows.map((o) => (
                <tr key={o.id} className="row-open" onDoubleClick={() => setCard(o)} title="Двойной клик — карточка ордера">
                  <td>
                    <CopyCell value={o.id} showToast={showToast} okText="ID ордера скопирован" />
                  </td>
                  <td className="nowrap">{formatDateTime(o.createdAt)}</td>
                  <td className="code">{o.internalId || '-'}</td>
                  <td>
                    <Badge tone={orderStatusTone(o.status)}>
                      {dictLabel(dict, 'OrderStatus', o.status)}
                    </Badge>
                  </td>
                  {/* Поле method бэк пока не отдаёт — до тех пор здесь «-». */}
                  <td>{dictLabel(dict, 'RequestMethod', o.method)}</td>
                  <td className="num strong">{formatNumber(o.amount)}</td>
                  <td className="url-cell">
                    {o.callbackUrl
                        ? <span className="ellipsis dim" title={o.callbackUrl}>{o.callbackUrl}</span>
                        : <span className="dim">-</span>}
                  </td>
                </tr>
            ))}
            </tbody>
          </table>
        </TableCard>

        {card && (
            <OrderCard
                order={card} dict={dict} timeoutSec={timeoutSec}
                showToast={showToast} onClose={() => setCard(null)}
                onChanged={(fresh) => { setCard(fresh); list.reload(); }}
            />
        )}
      </>
  );
}

/* ---- Карточка ордера ----
   Данные — из строки таблицы: список GET /api/client/order отдаёт все поля
   карточки (id, amount, status, internalId, createdAt, method, bank,
   details, callbackUrl). Статус и метод — подписи из словаря.
   «Дата истечения» по ТЗ вычисляется: дата создания + время активности
   ордеров клиента (orderTimeoutSeconds из профиля).
   «Отменить ордер» — для NEW и DISPUTE (ТЗ «Управление статусом
   ордера») и SEARCHING — «Поиск реквизитов» (ТЗ «Статусы „Поиск
   реквизитов“ и „Реквизиты не найдены“»); у «Реквизиты не найдены»
   кнопки нет. PATCH /order/{id}, затем карточка перечитывает ордер. */
const CANCELABLE_ORDER = ['NEW', 'DISPUTE', 'SEARCHING'];

function OrderCard({ order, dict, timeoutSec, showToast, onClose, onChanged }) {
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [canceling, setCanceling] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !confirmCancel) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, confirmCancel]);

  const cancelOrder = async () => {
    setCanceling(true);
    try {
      await api.cancelOrder(order.id);
      showToast('Ордер отменён', 'success');
    } catch (e) {
      // Например, статус ордера успели сменить, пока карточка была открыта, — 400 с текстом.
      showToast(e.message || 'Не удалось отменить ордер', 'error');
    }
    // В любом случае перечитываем ордер: статус на сервере мог измениться.
    let fresh = null;
    try { fresh = await api.order(order.id); } catch { /* останется прежняя версия */ }
    setCanceling(false);
    setConfirmCancel(false);
    onChanged?.(fresh && typeof fresh === 'object' ? { ...order, ...fresh } : order);
  };

  const created = Number(order.createdAt);
  const timeout = Number(timeoutSec);
  const expiresAt = Number.isFinite(created) && created > 0 && Number.isFinite(timeout) && timeout >= 0
      ? created + timeout * 1000
      : null;

  // Реквизиты ещё не выданы — в «Метод», «Банк», «Реквизиты» прочерк (ТЗ).
  // Метод в базе обязателен и у такого ордера всё равно заполнен, поэтому
  // признак — пустые реквизиты, а не пустой метод.
  const noDetails = !String(order.details ?? '').trim();
  const dash = <span className="dim">—</span>;
  const copyBtn = (value, okText) => (
      <button type="button" className="icon-btn icon-btn-sm" title="Скопировать" aria-label="Скопировать"
              onClick={() => copyText(value, showToast, okText)}>
        <i className="fa-regular fa-copy" />
      </button>
  );
  const withCopy = (value, okText, mono = true) => (value
      ? <span className="ocard-copy"><span className={mono ? 'code wrap' : 'wrap'}>{value}</span>{copyBtn(value, okText)}</span>
      : dash);

  return (
      <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
        <div className="modal ocard" role="dialog" aria-modal="true" aria-label="Карточка ордера">
          <div className="ocard-head">
            <span className="ocard-ico"><i className="fa-solid fa-receipt" /></span>
            <div className="ocard-title">
              <span className="ocard-eyebrow">Ордер</span>
              {withCopy(order.id, 'ID ордера скопирован')}
            </div>
            <button type="button" className="close-x" onClick={onClose} aria-label="Закрыть">×</button>
          </div>

          <div className="ocard-band">
            <div>
              <span className="ocard-eyebrow">Сумма</span>
              <span className="ocard-sum">{order.amount != null ? `${formatNumber(order.amount, 0)} ₽` : '—'}</span>
            </div>
            <div className="ocard-band-right">
              <span className="ocard-eyebrow">Статус</span>
              <Badge tone={orderStatusTone(order.status)}>{dictLabel(dict, 'OrderStatus', order.status)}</Badge>
            </div>
          </div>

          <div className="ocard-grid">
            <div className="ocard-field">
              <span className="ocard-eyebrow">ID в вашей системе</span>
              {withCopy(order.internalId, 'ID скопирован')}
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Дата создания</span>
              <span>{created ? formatDateTime(order.createdAt) : '—'}</span>
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Метод</span>
              {!noDetails && order.method ? <span>{dictLabel(dict, 'RequestMethod', order.method)}</span> : dash}
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Дата истечения</span>
              <span>{expiresAt ? formatDateTime(expiresAt) : '—'}</span>
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Банк</span>
              {!noDetails && order.bank ? <span>{order.bank}</span> : dash}
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Callback URL</span>
              {withCopy(order.callbackUrl, 'Callback URL скопирован', false)}
            </div>
            <div className="ocard-field">
              <span className="ocard-eyebrow">Реквизиты</span>
              {withCopy(order.details, 'Реквизиты скопированы')}
            </div>
          </div>

          <div className="modal-foot">
            {CANCELABLE_ORDER.includes(order.status) && (
                <button type="button" className="btn btn-danger" onClick={() => setConfirmCancel(true)} disabled={canceling}>
                  <i className="fa-solid fa-xmark" />
                  Отменить ордер
                </button>
            )}
            <button type="button" className="btn btn-primary" onClick={onClose}>Закрыть</button>
          </div>
        </div>

        {confirmCancel && (
            <CancelOrderDialog
                id={order.id}
                busy={canceling}
                onCancel={() => setConfirmCancel(false)}
                onApply={cancelOrder}
            />
        )}
      </div>
  );
}

/* Подтверждение отмены ордера. */
function CancelOrderDialog({ id, busy, onCancel, onApply }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm" role="dialog" aria-modal="true" aria-labelledby="co-title">
          <div className="modal-head">
            <h2 id="co-title">Отмена ордера</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="confirm-text">
              Отменить ордер <span className="code wrap">{id}</span>?
              <br />
              Действие необратимо.
            </p>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Не отменять</button>
            <button type="button" className="btn btn-danger" onClick={onApply} disabled={busy}>
              {busy && <i className="fa-solid fa-spinner fa-spin" />}
              Отменить ордер
            </button>
          </div>
        </div>
      </div>
  );
}

/* ==================== Раздел «Транзакции» ==================== */

const EMPTY_TX_FILTER = { id: '', ...EMPTY_DATE_FILTER };

function TransactionsSection({ dict, showToast }) {
  const list = usePagedList(api.transactions, showToast, 'Не удалось загрузить транзакции');
  const [draft, setDraft] = useState(EMPTY_TX_FILTER);

  const onSearch = () => list.search({ id: draft.id.trim(), ...dateFilterParams(draft) });
  const onReset = () => { setDraft(EMPTY_TX_FILTER); list.search({}); };

  return (
      <>
        <FilterPanel onSearch={onSearch} onReset={onReset} onRefresh={list.reload} refreshing={list.loading}>
          <div className="field field-wide">
            <label htmlFor="tx-id">ID транзакции</label>
            <input
                id="tx-id"
                type="text"
                placeholder="UUID транзакции"
                value={draft.id}
                onChange={(e) => setDraft((d) => ({ ...d, id: e.target.value }))}
            />
          </div>
          <DateFilter idPrefix="tx-date" value={draft} onChange={setDraft} />
        </FilterPanel>

        <TableCard
            list={list}
            emptyText="Транзакций не найдено"
            emptyIcon="fa-solid fa-right-left"
            totalLabel="Всего транзакций"
        >
          <table className="grid grid-tx">
            <thead>
            <tr>
              <th>ID транзакции</th>
              <th>Создана</th>
              <th>Операция</th>
              <th className="num">Сумма, ₽</th>
              <th>Тип</th>
              <th>Комментарий</th>
            </tr>
            </thead>
            <tbody>
            {list.rows.map((t, i) => {
              // CREDIT — зачисление: зелёная строка и «+»,
              // DEBIT — списание: красная строка и «-».
              const credit = t.operation === 'CREDIT';
              const debit = t.operation === 'DEBIT';
              const sign = credit ? '+' : debit ? '-' : '';
              const amount = Math.abs(Number(t.amount));
              return (
                  <tr
                      key={t.id ?? i}
                      className={`no-hover${credit ? ' tx-credit' : ''}${debit ? ' tx-debit' : ''}`}
                  >
                    <td>
                      <CopyCell value={t.id} showToast={showToast} okText="ID транзакции скопирован" />
                    </td>
                    <td className="nowrap">{formatDateTime(t.createdAt)}</td>
                    <td>{dictLabel(dict, 'Operation', t.operation)}</td>
                    <td className={`num strong${credit ? ' plus' : ''}${debit ? ' minus' : ''}`}>
                      {Number.isFinite(amount) ? `${sign}${formatNumber(amount)}` : '-'}
                    </td>
                    <td>
                      <Badge tone={TX_TYPE_TONE[t.type] || 'grey'}>
                        {dictLabel(dict, 'TransactionType', t.type)}
                      </Badge>
                    </td>
                    <td className="dim">{t.comment || '-'}</td>
                  </tr>
              );
            })}
            </tbody>
          </table>
        </TableCard>
      </>
  );
}

/* ==================== Раздел «Заявки на вывод» ==================== */

const EMPTY_WITHDRAWALS_FILTER = { id: '', status: '', address: '', ...EMPTY_DATE_FILTER };

function WithdrawalsSection({ dict, showToast }) {
  const list = usePagedList(api.withdrawals, showToast, 'Не удалось загрузить заявки');
  const [draft, setDraft] = useState(EMPTY_WITHDRAWALS_FILTER);
  const [creating, setCreating] = useState(false);  // окно создания
  const [toCancel, setToCancel] = useState(null);   // заявка в подтверждении
  const [canceling, setCanceling] = useState(false);

  const onSearch = () => list.search({
    id: draft.id.trim(),
    status: draft.status,
    address: draft.address.trim(),
    ...dateFilterParams(draft),
  });
  const onReset = () => { setDraft(EMPTY_WITHDRAWALS_FILTER); list.search({}); };

  const onCreated = () => {
    setCreating(false);
    showToast('Заявка создана', 'success');
    list.reloadFirst();
  };

  const cancel = async (row) => {
    setCanceling(true);
    try {
      await api.cancelWithdrawal(row.id);
      showToast('Заявка отменена', 'success');
    } catch (e) {
      // Кнопка есть только у NEW, но таблица могла устареть: пока окно
      // было открыто, заявку успели подтвердить. Бэк в этом случае
      // отвечает обычным 400, поэтому проверяем текущий статус сами.
      let alreadyProcessed = false;
      if (!e.unauthorized) {
        try {
          const { rows } = await api.withdrawals({ id: row.id });
          const fresh = rows.find((r) => r.id === row.id);
          alreadyProcessed = Boolean(fresh && fresh.status !== 'NEW');
        } catch { /* не удалось проверить — покажем исходную ошибку */ }
      }
      showToast(
          alreadyProcessed ? 'Заявку уже нельзя отменить' : (e.message || 'Не удалось отменить заявку'),
          'error',
      );
    } finally {
      setCanceling(false);
      setToCancel(null);
      list.reload();
    }
  };

  return (
      <>
        <FilterPanel
            onSearch={onSearch}
            onReset={onReset}
            onRefresh={list.reload}
            refreshing={list.loading}
            extra={(
                <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
                  <i className="fa-solid fa-plus" />
                  Создать заявку
                </button>
            )}
        >
          <div className="field field-wide">
            <label htmlFor="wd-id">ID заявки</label>
            <input
                id="wd-id"
                type="text"
                placeholder="UUID заявки"
                value={draft.id}
                onChange={(e) => setDraft((d) => ({ ...d, id: e.target.value }))}
            />
          </div>
          <SelectField
              id="wd-status"
              label="Статус"
              value={draft.status}
              items={dictItems(dict, 'WithdrawalRequestStatus')}
              onChange={(v) => setDraft((d) => ({ ...d, status: v }))}
          />
          <div className="field field-wide">
            <label htmlFor="wd-address">Адрес кошелька</label>
            <input
                id="wd-address"
                type="text"
                placeholder="Адрес USDT-кошелька"
                value={draft.address}
                onChange={(e) => setDraft((d) => ({ ...d, address: e.target.value }))}
            />
          </div>
          <DateFilter idPrefix="wd-date" value={draft} onChange={setDraft} />
        </FilterPanel>

        <TableCard
            list={list}
            emptyText="Заявок не найдено"
            emptyIcon="fa-solid fa-money-bill-transfer"
            totalLabel="Всего заявок"
        >
          <table className="grid grid-wd">
            <thead>
            <tr>
              <SortTh field="id" list={list}>ID заявки</SortTh>
              <SortTh field="status" list={list}>Статус</SortTh>
              <SortTh field="createdAt" list={list}>Создана</SortTh>
              <SortTh field="grossSourceAmount" list={list} className="num">Сумма заявки, ₽</SortTh>
              <SortTh field="commissionPercent" list={list} className="num">Комиссия</SortTh>
              <SortTh field="netSourceAmount" list={list} className="num">К выводу, ₽</SortTh>
              <SortTh field="rate" list={list} className="num">Курс ₽/USDT</SortTh>
              <SortTh field="targetAmount" list={list} className="num">Сумма, USDT</SortTh>
              <SortTh field="address" list={list}>Адрес кошелька</SortTh>
              <th className="c-act" aria-label="Действия" />
            </tr>
            </thead>
            <tbody>
            {list.rows.map((w) => (
                <tr key={w.id} className="no-hover">
                  <td>
                    <CopyCell value={w.id} showToast={showToast} okText="ID заявки скопирован" />
                  </td>
                  <td>
                    <Badge tone={WITHDRAWAL_STATUS_TONE[w.status] || 'grey'}>
                      {dictLabel(dict, 'WithdrawalRequestStatus', w.status)}
                    </Badge>
                  </td>
                  <td className="nowrap">{formatDateTime(w.createdAt)}</td>
                  <td className="num strong">{formatNumber(w.grossSourceAmount)}</td>
                  <td className="num">
                    {w.commissionPercent === null || w.commissionPercent === undefined
                        ? '-' : `${formatNumber(w.commissionPercent)} %`}
                  </td>
                  <td className="num strong">{formatNumber(w.netSourceAmount)}</td>
                  <td className="num">{formatRate(w.rate)}</td>
                  <td className="num strong">{formatNumber(w.targetAmount)}</td>
                  <td>
                    <CopyCell
                        value={w.address}
                        head={6}
                        tail={6}
                        showToast={showToast}
                        okText="Адрес скопирован"
                    />
                  </td>
                  <td className="c-act">
                    {w.status === 'NEW' && (
                        <button
                            type="button"
                            className="icon-btn icon-btn-danger"
                            title="Отменить заявку"
                            aria-label="Отменить заявку"
                            onClick={() => setToCancel(w)}
                        >
                          <i className="fa-solid fa-xmark" />
                        </button>
                    )}
                  </td>
                </tr>
            ))}
            </tbody>
          </table>
        </TableCard>

        {creating && (
            <CreateWithdrawalDialog
                onCancel={() => setCreating(false)}
                onCreated={onCreated}
                showToast={showToast}
            />
        )}
        {toCancel && (
            <CancelWithdrawalDialog
                row={toCancel}
                busy={canceling}
                onCancel={() => setToCancel(null)}
                onApply={() => cancel(toCancel)}
            />
        )}
      </>
  );
}

/* Окно создания заявки на вывод. */
function CreateWithdrawalDialog({ onCancel, onCreated, showToast }) {
  const [amount, setAmount] = useState('');
  const [address, setAddress] = useState('');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !saving) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, saving]);

  const submit = async () => {
    const next = {};
    if (!isPositiveInt(amount)) next.amount = 'Укажите целое число больше нуля';
    if (!isValidWalletAddress(address)) next.address = 'Укажите корректный адрес кошелька';
    setErrors(next);
    if (Object.keys(next).length) return;

    setSaving(true);
    try {
      await api.createWithdrawal(Number(amount.trim()), address.trim());
      onCreated();
    } catch (e) {
      // 400 про сумму («На балансе недостаточно средств.», «…меньше минимально
      // допустимой.») показываем под полем «Сумма» — рядом с тем, что исправлять.
      if (e.status === 400 && e.message && /баланс|сумм/i.test(e.message)) {
        setErrors({ amount: e.message });
        setSaving(false);
        return;
      }
      // 503 бэк отдаёт без пояснения («Service Unavailable»), когда вывод
      // невозможен: не настроена комиссия клиента или нет курса.
      showToast(
          e.status === 503
              ? 'Вывод временно недоступен. Попробуйте позже или обратитесь к администратору'
              : (e.message || 'Не удалось создать заявку'),
          'error',
      );
      setSaving(false);
    }
  };

  const onKeyDown = (e) => { if (e.key === 'Enter') submit(); };

  return (
      <div className="overlay">
        <div className="modal modal-sm">
          <div className="modal-head">
            <h2>Новая заявка на вывод</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={saving} aria-label="Закрыть">×</button>
          </div>

          <div className="modal-body">
            <div className="field">
              <label htmlFor="wd-new-amount">Сумма, ₽</label>
              <input
                  id="wd-new-amount"
                  type="text"
                  inputMode="numeric"
                  placeholder="0"
                  autoFocus
                  value={amount}
                  disabled={saving}
                  className={errors.amount ? 'invalid' : ''}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    if (errors.amount) setErrors((x) => ({ ...x, amount: '' }));
                  }}
                  onKeyDown={onKeyDown}
              />
              {errors.amount
                  ? <span className="field-error">{errors.amount}</span>
                  : <span className="field-hint">Целое число больше нуля</span>}
            </div>

            <div className="field">
              <label htmlFor="wd-new-address">Адрес USDT-кошелька</label>
              <input
                  id="wd-new-address"
                  type="text"
                  placeholder="Адрес, на который будет отправлен вывод"
                  value={address}
                  disabled={saving}
                  className={errors.address ? 'invalid' : ''}
                  onChange={(e) => {
                    setAddress(e.target.value);
                    if (errors.address) setErrors((x) => ({ ...x, address: '' }));
                  }}
                  onKeyDown={onKeyDown}
              />
              {errors.address && <span className="field-error">{errors.address}</span>}
            </div>
          </div>

          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>Отмена</button>
            <button type="button" className="btn btn-primary" onClick={submit} disabled={saving}>Создать</button>
          </div>
        </div>
      </div>
  );
}

/* Подтверждение отмены заявки. */
function CancelWithdrawalDialog({ row, busy, onCancel, onApply }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onCancel, busy]);

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm">
          <div className="modal-head">
            <h2>Отменить заявку?</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <p className="confirm-text">
              Заявка <span className="code" title={row.id}>{shorten(row.id)}</span> на сумму{' '}
              <b>{formatNumber(row.grossSourceAmount)} ₽</b> будет отменена. Отменить действие
              нельзя — для вывода понадобится создать новую заявку.
            </p>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Не отменять</button>
            <button type="button" className="btn btn-danger" onClick={onApply} disabled={busy}>Отменить заявку</button>
          </div>
        </div>
      </div>
  );
}