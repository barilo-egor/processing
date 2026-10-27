import { useEffect, useState } from 'react';

/* ============================================================
   Светлая / тёмная тема — общая для обоих кабинетов.

   Тема ставится атрибутом data-theme на <html>, цвета — в base.css.
   Пока пользователь сам не выбрал тему, она следует настройкам
   системы (и меняется вместе с ними). Выбор кнопкой запоминается
   в localStorage; ключ общий, поэтому кабинеты на одном адресе
   открываются в одной теме.
   ============================================================ */

const THEME_KEY = 'theme';

const media = typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

function storedTheme() {
  try {
    const v = localStorage.getItem(THEME_KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch { return null; }
}

const systemTheme = () => (media?.matches ? 'dark' : 'light');

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
}

/* Вызывается в main.jsx до первого рендера, чтобы страница
   не мигала светлой темой при сохранённой тёмной. */
export function initTheme() {
  applyTheme(storedTheme() || systemTheme());
}

/* Кнопка в шапке: луна — включить тёмную, солнце — включить светлую. */
export default function ThemeToggle() {
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || storedTheme() || systemTheme());

  useEffect(() => {
    // Сменилась системная тема — подхватываем, если пользователь свою не выбирал.
    const onSystem = () => {
      if (storedTheme()) return;
      const t = systemTheme();
      applyTheme(t);
      setTheme(t);
    };
    // Тему переключили в другой вкладке — переключаемся и здесь.
    const onStorage = (e) => {
      if (e.key !== THEME_KEY) return;
      const t = storedTheme() || systemTheme();
      applyTheme(t);
      setTheme(t);
    };
    media?.addEventListener?.('change', onSystem);
    window.addEventListener('storage', onStorage);
    return () => {
      media?.removeEventListener?.('change', onSystem);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(THEME_KEY, next); } catch { /* без запоминания */ }
    applyTheme(next);
    setTheme(next);
  };

  const label = theme === 'dark' ? 'Светлая тема' : 'Тёмная тема';
  return (
      <button type="button" className="theme-btn" onClick={toggle} title={label} aria-label={label}>
        <i className={`fa-solid ${theme === 'dark' ? 'fa-sun' : 'fa-moon'}`} />
      </button>
  );
}
