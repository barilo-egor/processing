import {useState} from 'react';

/* ============================================================
   Личный кабинет администратора API — каркас приложения.

   Что уже есть: боковое меню, шапка раздела, рабочая зона,
   тема под настройки системы (см. index.css), тосты.

   Что делаем после контрактов бэка: таблицы клиентов и ордеров
   с фильтрами и пагинацией, карточки с редактированием по месту.

   Разделы переключаются состоянием, без роутера: адрес в строке
   браузера не меняется. Если при развёртывании понадобятся
   отдельные адреса /clients и /orders — добавим react-router.
   ============================================================ */

const SECTIONS = [
  { id: 'clients', title: 'Клиенты', icon: 'fa-solid fa-users' },
  { id: 'orders', title: 'Ордера', icon: 'fa-solid fa-receipt' },
];

export default function App() {
  const [section, setSection] = useState('clients');
  const [toast, setToast] = useState(null);

  const showToast = (message, type = 'info') => {
    setToast({ message, type });
    window.clearTimeout(showToast._t);
    showToast._t = window.setTimeout(() => setToast(null), 2600);
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
        </header>

        <main className="content">
          {section === 'clients' && <ClientsSection showToast={showToast} />}
          {section === 'orders' && <OrdersSection showToast={showToast} />}
        </main>
      </div>

      {toast && <div className={`toast toast-${toast.type}`}>{toast.message}</div>}
    </div>
  );
}

/* ==================== Клиенты ==================== */
function ClientsSection() {
  return (
    <Placeholder
      icon="fa-solid fa-users"
      title="Раздел «Клиенты»"
      text="Таблица, фильтр и карточка клиента появятся после подключения к API."
    />
  );
}

/* ==================== Ордера ==================== */
function OrdersSection() {
  return (
    <Placeholder
      icon="fa-solid fa-receipt"
      title="Раздел «Ордера»"
      text="Таблица, фильтр и карточка ордера появятся после подключения к API."
    />
  );
}

/* ---- Заглушка раздела ---- */
function Placeholder({ icon, title, text }) {
  return (
    <div className="card placeholder">
      <i className={icon} />
      <h2>{title}</h2>
      <p>{text}</p>
    </div>
  );
}
