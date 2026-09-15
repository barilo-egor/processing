import { useCallback, useEffect, useRef, useState } from 'react';
import { api, isValidCallbackUrl } from './api.js';

/* ============================================================
   Личный кабинет клиента API.

   Одна страница — «Профиль», два независимых блока:
   «Callback URL» и «API-токены». Каждый сохраняется отдельно.

   Регистрации в интерфейсе нет: учётные записи заводит
   администратор в кабинете администратора API.
   ============================================================ */

// Больше десяти активных токенов у клиента быть не может.
const TOKENS_LIMIT = 10;

const SECTIONS = [
  { id: 'profile', title: 'Профиль', icon: 'fa-solid fa-user-gear' },
];

export default function App() {
  const [section, setSection] = useState('profile');
  const [toast, setToast] = useState(null);
  // Профиль грузим один раз: логин нужен в шапке, id — для сохранения
  // Callback URL.
  const [profile, setProfile] = useState(null);
  const [loadingProfile, setLoadingProfile] = useState(true);

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type, key: Date.now() });
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);

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

  const current = SECTIONS.find((s) => s.id === section);

  return (
      <div className="layout">
        <aside className="sidebar">
          <div className="brand">
            <span className="brand-ico"><i className="fa-solid fa-key" /></span>
            <span className="brand-name">Кабинет клиента</span>
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
            <AccountMenu username={profile?.username} />
          </header>
          <main className="content">
            <CallbackUrlBlock
                profile={profile}
                loading={loadingProfile}
                showToast={showToast}
            />
            <ApiKeysBlock showToast={showToast} />
          </main>
        </div>

        {toast && <div className={`toast toast-${toast.type}`}>{toast.message}</div>}
      </div>
  );
}


/* ==================== Меню аккаунта ==================== */
/* Логин клиента в правом углу верхней панели. Клик раскрывает меню
   с выходом; повторный клик, клик вне меню и Escape закрывают. */
function AccountMenu({ username }) {
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
              <a className="account-item" href="/logout" role="menuitem">
                <i className="fa-solid fa-arrow-right-from-bracket" />
                Выйти
              </a>
            </div>
        )}
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
      await api.saveCallbackUrl(clientId, next);
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
                  placeholder="Например, боевой сервер"
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