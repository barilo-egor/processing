import { lazy, Suspense, useEffect, useState } from 'react';

/* ============================================================
   Документация — общая для обоих кабинетов.

   Список страниц приходит при открытии кабинета (GET /docs) — бэк сам
   отбирает страницы по роли пользователя. В меню — пункт «Документация»
   с подпунктами-страницами. Если страниц нет (или список не загрузился),
   пункта в меню нет совсем. Раздел страницы — 'doc:{id}'.
   Запросы у кабинетов разные (/api/support, /api/client), поэтому
   загрузку страницы передаёт кабинет: <DocPage id={…} load={api.doc} />.
   ============================================================ */

export const DOC_PREFIX = 'doc:';
const Markdown = lazy(() => import('./Markdown.jsx'));
export const docIdOf = (section) => (section.startsWith(DOC_PREFIX) ? section.slice(DOC_PREFIX.length) : null);

/* Открытая страница документации — в адресе: ?doc={id}, раздел страницы — #якорь. */
const DOC_PARAM = 'doc';

export function sectionFromUrl(fallback) {
  const id = new URLSearchParams(window.location.search).get(DOC_PARAM);
  return id ? DOC_PREFIX + id : fallback;
}

export function syncDocUrl(section) {
  const url = new URL(window.location.href);
  const id = docIdOf(section);
  if (url.searchParams.get(DOC_PARAM) === id) return;
  if (id) url.searchParams.set(DOC_PARAM, id);
  else url.searchParams.delete(DOC_PARAM);
  url.hash = '';
  window.history.replaceState(window.history.state, '', url);
}

/* Пункт меню с подпунктами: раскрывается и сворачивается по клику,
   с активным подпунктом — раскрыт. */
export function NavGroup({ title, icon, active, collapsed, children }) {
  const [open, setOpen] = useState(active);
  useEffect(() => { if (active) setOpen(true); }, [active]);

  return (
      <div className="nav-group">
        <button
            type="button"
            className={`nav-item nav-group-head${active ? ' has-active' : ''}`}
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            title={collapsed ? title : undefined}
        >
          <i className={icon} />
          <span>{title}</span>
          <i className={`fa-solid fa-chevron-${open ? 'up' : 'down'} nav-caret`} />
        </button>
        {open && <div className="nav-sub">{children}</div>}
      </div>
  );
}

/* Пункт меню «Документация» со страницами-подпунктами. */
export function DocsNav({ docs, section, collapsed, onOpen }) {
  const activeId = docIdOf(section);
  if (!docs.length) return null;
  return (
      <NavGroup title="Документация" icon="fa-solid fa-book" active={Boolean(activeId)} collapsed={collapsed}>
        {docs.map((d) => (
            <button
                key={d.id}
                type="button"
                className={`nav-item nav-sub-item${activeId === d.id ? ' active' : ''}`}
                onClick={() => onOpen(DOC_PREFIX + d.id)}
                title={collapsed ? d.title : undefined}
            >
              <i className="fa-regular fa-file-lines" />
              <span>{d.title}</span>
            </button>
        ))}
      </NavGroup>
  );
}

/* Страница документации: GET /docs/{id}, текст — Markdown (GFM). */
/* Заголовок страницы (title) — в верхней панели кабинета, как у остальных разделов. */
export function DocPage({ id, load }) {
  const [state, setState] = useState({ loading: true, page: null, error: '' });

  useEffect(() => {
    let alive = true;
    setState({ loading: true, page: null, error: '' });
    load(id)
        .then((page) => { if (alive) setState({ loading: false, page, error: '' }); })
        .catch((e) => {
          if (!alive) return;
          // 404 — страницы нет или она недоступна роли пользователя.
          setState({
            loading: false,
            page: null,
            error: e.status === 404 ? 'Страница не найдена' : (e.message || 'Не удалось загрузить страницу'),
          });
        });
    return () => { alive = false; };
  }, [id, load]);

  if (state.loading) {
    return <div className="state"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>;
  }
  if (state.error) {
    return <div className="state"><i className="fa-regular fa-file-excel" /> {state.error}</div>;
  }
  return (
      <article className="doc-page">
        <Suspense fallback={<div className="state"><i className="fa-solid fa-spinner fa-spin" /> Загрузка…</div>}>
          <Markdown source={state.page?.content} />
        </Suspense>
      </article>
  );
}
