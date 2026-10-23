/* ============================================================
   Показ страниц документации (Markdown → React).

   Бэк отдаёт текст страницы в GitHub Flavored Markdown (см. коллекцию:
   «Страница документации»). В тексте только: заголовки ##/###, абзацы,
   нумерованные и маркированные списки, **жирный**, `код`, таблицы GFM
   и разделитель ---. HTML-вставок и картинок нет.

   Поэтому вместо сторонней библиотеки — небольшой разбор этого набора:
   без новых зависимостей в package.json. Всё выводится как React-элементы
   (без dangerouslySetInnerHTML), так что текст страницы не может вставить
   в кабинет свою разметку или скрипт.
   ============================================================ */

const RE_HEADING = /^(#{1,6})\s+(.*)$/;
const RE_HR = /^\s*([-*_])(\s*\1){2,}\s*$/;
const RE_UL = /^(\s*)[-*+]\s+(.*)$/;
const RE_OL = /^(\s*)(\d+)[.)]\s+(.*)$/;
const RE_TABLE_SEP = /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/;

const isTableRow = (line) => /^\s*\|.*\|\s*$/.test(line);

/* Ячейки строки таблицы: «| a | b |» → ['a', 'b']. Экранированный \| — часть текста. */
function splitRow(line) {
    const inner = line.trim().replace(/^\|/, '').replace(/\|$/, '');
    const cells = [];
    let cur = '';
    for (let i = 0; i < inner.length; i += 1) {
        if (inner[i] === '\\' && inner[i + 1] === '|') { cur += '|'; i += 1; continue; }
        if (inner[i] === '|') { cells.push(cur.trim()); cur = ''; continue; }
        cur += inner[i];
    }
    cells.push(cur.trim());
    return cells;
}

function alignOf(sep) {
    const s = sep.trim();
    if (s.startsWith(':') && s.endsWith(':')) return 'center';
    if (s.endsWith(':')) return 'right';
    return undefined;
}

/* ---------- Строчная разметка: `код`, **жирный**, *курсив*, [ссылка](url) ---------- */
const RE_INLINE = /(`+)([\s\S]*?[^`])\1(?!`)|\*\*([\s\S]+?)\*\*|__([\s\S]+?)__|\*([^*\s][^*]*?)\*|\[([^\]]+)\]\(([^)\s]+)\)/g;

function renderInline(text, keyBase = 'i') {
    const out = [];
    let last = 0;
    let m;
    let n = 0;
    // Своё регулярное выражение на каждый вызов: renderInline вызывается рекурсивно
    // (жирный внутри ссылки и т. п.), а у общего /g-выражения сбился бы lastIndex.
    const re = new RegExp(RE_INLINE.source, 'g');
    while ((m = re.exec(text)) !== null) {
        if (m.index > last) out.push(text.slice(last, m.index));
        const key = `${keyBase}-${n += 1}`;
        if (m[1]) {
            out.push(<code key={key}>{m[2].replace(/^ (.*) $/, '$1')}</code>);
        } else if (m[3] !== undefined || m[4] !== undefined) {
            out.push(<strong key={key}>{renderInline(m[3] ?? m[4], key)}</strong>);
        } else if (m[5] !== undefined) {
            out.push(<em key={key}>{renderInline(m[5], key)}</em>);
        } else if (m[6] !== undefined) {
            // Ссылки — только http(s), открываются в новой вкладке.
            const href = /^https?:\/\//i.test(m[7]) ? m[7] : null;
            out.push(href
                ? <a key={key} href={href} target="_blank" rel="noopener noreferrer">{renderInline(m[6], key)}</a>
                : <span key={key}>{renderInline(m[6], key)}</span>);
        }
        last = re.lastIndex;
    }
    if (last < text.length) out.push(text.slice(last));
    return out;
}

/* ---------- Списки (с вложенностью по отступу) ---------- */
function parseList(lines, start) {
    const first = lines[start];
    const ordered = !RE_UL.test(first) && RE_OL.test(first);
    const baseIndent = (ordered ? RE_OL : RE_UL).exec(first)[1].length;
    const startNum = ordered ? Number(RE_OL.exec(first)[2]) : 1;
    const items = [];
    let i = start;

    while (i < lines.length) {
        const line = lines[i];
        const mu = RE_UL.exec(line);
        const mo = RE_OL.exec(line);
        const m = ordered ? (mu ? null : mo) : mu;
        const indent = m ? m[1].length : -1;

        if (m && indent === baseIndent) {
            items.push({ text: ordered ? m[3] : m[2], children: [] });
            i += 1;
            continue;
        }
        if (!items.length) break;
        // Вложенный список — пункт с большим отступом.
        if ((mu || mo) && ((mu || mo)[1].length > baseIndent)) {
            const nested = parseList(lines, i);
            items[items.length - 1].children.push(nested.node);
            i = nested.next;
            continue;
        }
        // Продолжение текста пункта на следующей строке (с отступом).
        if (line.trim() && /^\s+/.test(line) && !(mu || mo)) {
            items[items.length - 1].text += ` ${line.trim()}`;
            i += 1;
            continue;
        }
        break;
    }

    const Tag = ordered ? 'ol' : 'ul';
    const node = (
        <Tag key={`l-${start}`} start={ordered && startNum !== 1 ? startNum : undefined}>
            {items.map((it, k) => (
                <li key={k}>{renderInline(it.text, `l${start}-${k}`)}{it.children}</li>
            ))}
        </Tag>
    );
    return { node, next: i };
}

/* ---------- Блоки ---------- */
function parseBlocks(src) {
    const lines = String(src || '').replace(/\r\n?/g, '\n').split('\n');
    const blocks = [];
    let i = 0;

    const startsBlock = (line, next) => RE_HEADING.test(line) || RE_HR.test(line)
        || RE_UL.test(line) || RE_OL.test(line)
        || (isTableRow(line) && next !== undefined && RE_TABLE_SEP.test(next));

    while (i < lines.length) {
        const line = lines[i];

        if (!line.trim()) { i += 1; continue; }

        const h = RE_HEADING.exec(line);
        if (h) {
            // # в тексте не бывает (вынесен в title), но на всякий случай — как ##.
            const level = Math.min(Math.max(h[1].length, 2), 4);
            const Tag = `h${level}`;
            blocks.push(<Tag key={`h-${i}`}>{renderInline(h[2].replace(/\s+#+\s*$/, ''), `h${i}`)}</Tag>);
            i += 1;
            continue;
        }

        if (RE_HR.test(line)) {
            blocks.push(<hr key={`hr-${i}`} />);
            i += 1;
            continue;
        }

        if (isTableRow(line) && i + 1 < lines.length && RE_TABLE_SEP.test(lines[i + 1])) {
            const head = splitRow(line);
            const aligns = splitRow(lines[i + 1]).map(alignOf);
            const rows = [];
            i += 2;
            while (i < lines.length && isTableRow(lines[i])) {
                rows.push(splitRow(lines[i]));
                i += 1;
            }
            blocks.push(
                <div className="doc-table" key={`t-${i}`}>
                    <table>
                        <thead>
                        <tr>{head.map((c, k) => <th key={k} style={{ textAlign: aligns[k] }}>{renderInline(c, `th${i}-${k}`)}</th>)}</tr>
                        </thead>
                        <tbody>
                        {rows.map((r, ri) => (
                            <tr key={ri}>
                                {head.map((_, k) => (
                                    <td key={k} style={{ textAlign: aligns[k] }}>{renderInline(r[k] ?? '', `td${i}-${ri}-${k}`)}</td>
                                ))}
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>,
            );
            continue;
        }

        if (RE_UL.test(line) || RE_OL.test(line)) {
            const list = parseList(lines, i);
            blocks.push(list.node);
            i = list.next;
            continue;
        }

        // Абзац: строки до пустой или до начала другого блока; переносы внутри — пробелы (как в GFM).
        const para = [line.trim()];
        i += 1;
        while (i < lines.length && lines[i].trim() && !startsBlock(lines[i], lines[i + 1])) {
            para.push(lines[i].trim());
            i += 1;
        }
        blocks.push(<p key={`p-${i}`}>{renderInline(para.join(' '), `p${i}`)}</p>);
    }

    return blocks;
}

export default function Markdown({ source }) {
    return <div className="doc-content">{parseBlocks(source)}</div>;
}