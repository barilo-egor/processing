import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import rehypeSlug from 'rehype-slug';
import { createLowlight } from 'lowlight';
import bash from 'highlight.js/lib/languages/bash';
import http from 'highlight.js/lib/languages/http';
import json from 'highlight.js/lib/languages/json';

const SPY_OFFSET = 96;

const lowlight = createLowlight({ bash, http, json });

const RE_ENDPOINT = /^(GET|POST|PUT|PATCH|DELETE) (\/\S*)$/;
const RE_HTTP_STATUS = /^([1-5]\d\d) ([A-Za-z][A-Za-z -]*)$/;
const RE_ENUM = /^[A-Z][A-Z0-9_]*$/;
const RE_FINAL_STATUS = /(^|[.!?]\s+)Конечный статус/;
const RE_MSG_TOKEN = /(<[^<>]+>|[A-Za-z][A-Za-z0-9-]*)/;
const MESSAGE_HEAD = 'Сообщение';
const NOWRAP_MAX = 24;

const ALERTS = {
    NOTE: { label: 'Примечание', icon: 'fa-solid fa-circle-info' },
    TIP: { label: 'Рекомендация', icon: 'fa-regular fa-lightbulb' },
    IMPORTANT: { label: 'Важно', icon: 'fa-solid fa-circle-exclamation' },
    WARNING: { label: 'Внимание', icon: 'fa-solid fa-triangle-exclamation' },
    CAUTION: { label: 'Осторожно', icon: 'fa-solid fa-circle-xmark' },
};

const STATUS_TONE = {
    SEARCHING: 'cyan',
    DETAILS_NOT_FOUND: 'red',
    NEW: 'blue',
    SUCCESS: 'green',
    CANCELED: 'grey',
    TIMEOUT: 'amber',
    DISPUTE: 'purple',
};

const isExternal = (href) => /^https?:\/\//i.test(href || '');

const el = (tagName, className, children = [], extra = {}) => ({
    type: 'element', tagName, properties: { className: [].concat(className), ...extra }, children,
});
const text = (value) => ({ type: 'text', value });
const isEl = (n, tag) => n?.type === 'element' && (!tag || n.tagName === tag);
const textOf = (n) => (n.type === 'text' ? n.value : (n.children ?? []).map(textOf).join(''));
const elements = (n) => (n.children ?? []).filter((c) => isEl(c));
const meaningful = (n) => (n.children ?? []).filter((c) => !(c.type === 'text' && !c.value.trim()));
const addClass = (n, cls) => { n.properties.className = [...(n.properties.className ?? []), cls]; };

function endpoint(p) {
    const kids = meaningful(p);
    const m = kids.length === 1 && isEl(kids[0], 'code') ? RE_ENDPOINT.exec(textOf(kids[0])) : null;
    if (!m) return;
    Object.assign(p, el('div', 'doc-endpoint', [], { dataMethod: m[1], dataPath: m[2] }));
}

function alert(quote) {
    const first = elements(quote)[0];
    const head = first && isEl(first, 'p') ? first.children[0] : null;
    const m = head?.type === 'text' ? /^\[!(\w+)\][ \t]*([^\n]*)\n?/.exec(head.value) : null;
    const kind = m && ALERTS[m[1].toUpperCase()];
    if (!kind) return;
    head.value = head.value.slice(m[0].length);
    if (!textOf(first).trim()) quote.children.splice(quote.children.indexOf(first), 1);
    const title = el('div', 'doc-alert-title', [el('i', kind.icon.split(' ')), text(m[2].trim() || kind.label)]);
    Object.assign(quote, el('div', ['doc-alert', `doc-alert-${m[1].toLowerCase()}`], [title, ...quote.children]));
}

function httpBadge(code, short) {
    const m = RE_HTTP_STATUS.exec(textOf(code));
    if (!m) return false;
    const children = short ? [text(m[1])] : [el('b', [], [text(m[1])]), text(` ${m[2]}`)];
    Object.assign(code, el('span', ['doc-http', `doc-http-${m[1][0]}xx`], children, { title: `${m[1]} ${m[2]}` }));
    return true;
}

function messages(td) {
    td.children = td.children.filter((c) => !isEl(c, 'br')).map((c) => {
        if (!isEl(c, 'code') || !/[А-Яа-яЁё]/.test(textOf(c))) return c;
        const parts = textOf(c).split(RE_MSG_TOKEN)
            .map((part, k) => (k % 2 === 1 ? el('code', [], [text(part)]) : text(part)))
            .filter((n) => textOf(n));
        return el('span', 'doc-msg', parts);
    });
}

function statusCell(td, row) {
    const kids = meaningful(td);
    const code = kids.length === 1 && isEl(kids[0], 'code') ? textOf(kids[0]) : null;
    if (!code || !RE_ENUM.test(code)) return;
    const badge = el('span', ['doc-badge', `doc-tone-${STATUS_TONE[code] ?? 'grey'}`], [text(code)]);
    const final = elements(row).some((cell) => RE_FINAL_STATUS.test(textOf(cell)))
        ? [el('i', ['fa-solid', 'fa-lock', 'doc-final'], [], { title: 'Конечный статус' })]
        : [];
    td.children = [badge, ...final];
}

function mergeStatusRows(rows) {
    let group = null;
    for (const row of rows) {
        const first = elements(row)[0];
        const key = first && meaningful(first).length === 1 && first.children.some((c) => isEl(c, 'span')
            && c.properties.className.includes('doc-http')) ? textOf(first) : null;
        if (key && group?.key === key) {
            group.cell.properties.rowSpan = (group.cell.properties.rowSpan ?? 1) + 1;
            row.children.splice(row.children.indexOf(first), 1);
        } else {
            group = key ? { key, cell: first } : null;
        }
    }
}

function table(node) {
    const thead = elements(node).find((c) => isEl(c, 'thead'));
    const tbody = elements(node).find((c) => isEl(c, 'tbody'));
    const heads = thead ? elements(elements(thead)[0]).map((th) => textOf(th).trim()) : [];
    const rows = tbody ? elements(tbody).filter((r) => isEl(r, 'tr')) : [];

    for (const row of rows) {
        elements(row).forEach((td, k) => {
            if (heads[k] === MESSAGE_HEAD) messages(td);
            if (k === 0 && heads[0] === 'Статус') statusCell(td, row);
            elements(td).filter((c) => isEl(c, 'code')).forEach((c) => httpBadge(c, true));
            if (textOf(td).length <= NOWRAP_MAX) addClass(td, 'doc-nowrap');
        });
    }
    mergeStatusRows(rows);
}

function highlight(pre) {
    const code = elements(pre)[0];
    const lang = (code?.properties.className ?? []).find((c) => String(c).startsWith('language-'))
        ?.slice('language-'.length);
    if (!lang || !lowlight.registered(lang)) return;
    code.children = lowlight.highlight(lang, textOf(code)).children;
}

function rehypeDoc() {
    return (tree) => {
        const walk = (node) => {
            if (isEl(node, 'table')) { table(node); return; }
            if (isEl(node, 'pre')) { highlight(node); return; }
            if (isEl(node, 'p')) endpoint(node);
            if (isEl(node, 'blockquote')) alert(node);
            if (isEl(node, 'code')) httpBadge(node, false);
            (node.children ?? []).forEach(walk);
        };
        walk(tree);
    };
}

const REHYPE = [rehypeRaw, rehypeSanitize, rehypeSlug, rehypeDoc];

function copyText(value, onDone) {
    navigator.clipboard?.writeText(value).then(onDone).catch(() => {});
}

function useFlag() {
    const [on, setOn] = useState(false);
    const raise = () => {
        setOn(true);
        setTimeout(() => setOn(false), 1500);
    };
    return [on, raise];
}

function CodeBlock({ node, children }) {
    const ref = useRef(null);
    const [copied, flagCopied] = useFlag();
    const cls = node?.children?.[0]?.properties?.className ?? [];
    const lang = cls.find((c) => String(c).startsWith('language-'))?.slice('language-'.length);

    return (
        <div className="doc-code">
            <div className="doc-code-head">
                <span>{lang || 'text'}</span>
                <button type="button" onClick={() => copyText(ref.current?.textContent ?? '', flagCopied)} title="Скопировать">
                    <i className={copied ? 'fa-solid fa-check' : 'fa-regular fa-copy'} />
                    {copied ? 'Скопировано' : 'Копировать'}
                </button>
            </div>
            <pre ref={ref}>{children}</pre>
        </div>
    );
}

function Endpoint({ method, path }) {
    const [copied, flagCopied] = useFlag();
    return (
        <div className="doc-endpoint">
            <span className={`doc-method doc-method-${method.toLowerCase()}`}>{method}</span>
            <code className="doc-endpoint-path">{path}</code>
            <button type="button" className="doc-endpoint-copy" onClick={() => copyText(path, flagCopied)} title="Скопировать путь">
                <i className={copied ? 'fa-solid fa-check' : 'fa-regular fa-copy'} />
            </button>
        </div>
    );
}

function Heading({ level, id, children, onAnchor }) {
    const Tag = `h${level}`;
    const [copied, flagCopied] = useFlag();
    if (!id) return <Tag>{children}</Tag>;
    const open = (e) => {
        onAnchor(e, `#${id}`);
        copyText(window.location.href, flagCopied);
    };
    return (
        <Tag id={id} className="doc-heading">
            {children}
            <a href={`#${id}`} className="doc-heading-link" onClick={open} title="Скопировать ссылку на раздел">
                <i className={copied ? 'fa-solid fa-check' : 'fa-solid fa-link'} />
            </a>
        </Tag>
    );
}

export default function Markdown({ source }) {
    const ref = useRef(null);
    const [toc, setToc] = useState([]);
    const [activeId, setActiveId] = useState(null);

    const findAnchor = (id) => ref.current?.querySelector(`[id="${CSS.escape(id)}"]`);

    useEffect(() => {
        const headings = ref.current?.querySelectorAll('.doc-content h2[id], .doc-content h3[id]') ?? [];
        setToc(Array.from(headings, (h) => ({
            id: h.id,
            title: h.textContent,
            level: Number(h.tagName[1]),
        })));
        const hash = decodeURIComponent(window.location.hash.slice(1));
        if (hash) findAnchor(hash)?.scrollIntoView({ block: 'start' });
    }, [source]);

    useEffect(() => {
        if (toc.length < 2) return undefined;
        const update = () => {
            const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
            let current = toc[0].id;
            for (const t of toc) {
                const h = document.getElementById(t.id);
                if (h && h.getBoundingClientRect().top <= SPY_OFFSET) current = t.id;
            }
            setActiveId(atBottom ? toc[toc.length - 1].id : current);
        };
        update();
        window.addEventListener('scroll', update, { passive: true });
        window.addEventListener('resize', update);
        return () => {
            window.removeEventListener('scroll', update);
            window.removeEventListener('resize', update);
        };
    }, [toc]);

    const openAnchor = (e, href) => {
        e.preventDefault();
        const id = decodeURIComponent(href.slice(1));
        const target = findAnchor(id);
        if (!target) return;
        window.history.replaceState(window.history.state, '', `#${encodeURIComponent(id)}`);
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        setActiveId(id);
    };

    const components = {
        h1: ({ node, ...props }) => <Heading level={2} {...props} onAnchor={openAnchor} />,
        h2: ({ node, ...props }) => <Heading level={2} {...props} onAnchor={openAnchor} />,
        h3: ({ node, ...props }) => <Heading level={3} {...props} onAnchor={openAnchor} />,
        h5: ({ node, ...props }) => <h4 {...props} />,
        h6: ({ node, ...props }) => <h4 {...props} />,
        a: ({ node, href, children }) => {
            if (href?.startsWith('#')) return <a href={href} onClick={(e) => openAnchor(e, href)}>{children}</a>;
            if (isExternal(href)) return <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>;
            return <span>{children}</span>;
        },
        div: ({ node, ...props }) => (node.properties.className?.includes('doc-endpoint')
            ? <Endpoint method={node.properties.dataMethod} path={node.properties.dataPath} />
            : <div {...props} />),
        table: ({ node, ...props }) => <div className="doc-table"><table {...props} /></div>,
        pre: CodeBlock,
    };

    const hasToc = toc.length > 1;

    return (
        <div ref={ref} className={`doc-layout${hasToc ? ' with-toc' : ''}`}>
            <div className="doc-content">
                <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={REHYPE} components={components}>
                    {String(source || '')}
                </ReactMarkdown>
            </div>
            {hasToc && (
                <nav className="doc-toc" aria-label="Содержание">
                    <div className="doc-toc-title"><i className="fa-solid fa-list-ul" /> На этой странице</div>
                    <ul>
                        {toc.map((t) => (
                            <li key={t.id}>
                                <a
                                    href={`#${t.id}`}
                                    className={`doc-toc-l${t.level}${activeId === t.id ? ' active' : ''}`}
                                    onClick={(e) => openAnchor(e, `#${t.id}`)}
                                >
                                    {t.title}
                                </a>
                            </li>
                        ))}
                    </ul>
                </nav>
            )}
        </div>
    );
}
