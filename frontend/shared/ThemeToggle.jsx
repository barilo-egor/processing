import { useEffect, useState } from 'react';

/* ============================================================
   Тема оформления — общая для обоих кабинетов.

   Три варианта, как переключатель в шапке:
     «Как в системе» (монитор) — светлая или тёмная по настройке ОС
                                  и меняется вместе с ней;
     «Светлая» (солнце), «Тёмная» (луна) — всегда эта тема.
   Тема ставится атрибутом data-theme на <html> (всегда 'light' или
   'dark'), цвета — в base.css. Выбор «Светлая» / «Тёмная» запоминается
   в localStorage (ключ общий, поэтому кабинеты на одном адресе
   открываются в одной теме); «Как в системе» — это отсутствие записи,
   он же вариант по умолчанию.
   ============================================================ */

const THEME_KEY = 'theme';

const media = typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

// Сохранённый выбор: 'light' | 'dark' | 'system' (записи нет).
function storedMode() {
  try {
    const v = localStorage.getItem(THEME_KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch { return 'system'; }
}

const systemTheme = () => (media?.matches ? 'dark' : 'light');
const resolve = (mode) => (mode === 'system' ? systemTheme() : mode);

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
}

/* Вызывается в main.jsx до первого рендера, чтобы страница
   не мигала светлой темой при сохранённой тёмной. */
export function initTheme() {
  applyTheme(resolve(storedMode()));
}

const MODES = [
  { mode: 'system', icon: 'fa-solid fa-desktop', label: 'Как в системе' },
  { mode: 'light', icon: 'fa-regular fa-sun', label: 'Светлая тема' },
  { mode: 'dark', icon: 'fa-regular fa-moon', label: 'Тёмная тема' },
];

/* Переключатель в шапке: три кнопки, выбранная подсвечена. */
export default function ThemeToggle() {
  const [mode, setMode] = useState(storedMode);

  useEffect(() => {
    // Сменилась системная тема — подхватываем, если выбрано «Как в системе».
    const onSystem = () => { if (storedMode() === 'system') applyTheme(systemTheme()); };
    // Тему переключили в другой вкладке — переключаемся и здесь.
    const onStorage = (e) => {
      if (e.key !== THEME_KEY) return;
      const m = storedMode();
      setMode(m);
      applyTheme(resolve(m));
    };
    media?.addEventListener?.('change', onSystem);
    window.addEventListener('storage', onStorage);
    return () => {
      media?.removeEventListener?.('change', onSystem);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  const choose = (next) => {
    try {
      if (next === 'system') localStorage.removeItem(THEME_KEY);
      else localStorage.setItem(THEME_KEY, next);
    } catch { /* без запоминания */ }
    setMode(next);
    applyTheme(resolve(next));
  };

  return (
      <div className="theme-switch" role="radiogroup" aria-label="Тема оформления">
        {MODES.map((m) => (
            <button
                key={m.mode}
                type="button"
                role="radio"
                aria-checked={mode === m.mode}
                className={`theme-opt${mode === m.mode ? ' active' : ''}`}
                onClick={() => choose(m.mode)}
                title={m.label}
                aria-label={m.label}
            >
              <i className={m.icon} />
            </button>
        ))}
      </div>
  );
}