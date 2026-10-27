import { useEffect } from 'react';

/* ============================================================
   Окно подтверждения «Да / Нет» — общее для обоих кабинетов.

   Открывается поверх карточек (overlay-top). Escape и клик по
   затемнению — то же, что «Нет»; пока идёт запрос (busy), окно
   не закрывается и кнопки неактивны.
     title        — заголовок окна, по умолчанию «Подтверждение»
     children     — текст вопроса
     confirmLabel — подпись кнопки подтверждения, по умолчанию «Да»
     cancelLabel  — подпись кнопки отказа, по умолчанию «Нет»
     confirmClass — класс кнопки подтверждения (btn-primary, btn-danger…)
     confirmIcon  — иконка кнопки подтверждения (классы Font Awesome)
   ============================================================ */
export default function ConfirmDialog({
  title = 'Подтверждение',
  children,
  confirmLabel = 'Да',
  cancelLabel = 'Нет',
  confirmClass = 'btn-primary',
  confirmIcon = '',
  busy = false,
  onConfirm,
  onCancel,
}) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key !== 'Escape' || busy) return;
      // Escape закрывает только это окно, а не карточку под ним.
      e.stopImmediatePropagation();
      onCancel();
    };
    // В фазе перехвата — раньше обработчиков карточек под окном.
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onCancel, busy]);

  return (
      <div className="overlay overlay-top" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
        <div className="modal modal-sm" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
          <div className="modal-head">
            <h2 id="confirm-title">{title}</h2>
            <button type="button" className="close-x" onClick={onCancel} disabled={busy} aria-label="Закрыть">×</button>
          </div>
          <div className="modal-body">
            <div className="confirm-text">{children}</div>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>{cancelLabel}</button>
            <button type="button" className={`btn ${confirmClass}`} onClick={onConfirm} disabled={busy} autoFocus>
              {busy
                  ? <i className="fa-solid fa-spinner fa-spin" />
                  : confirmIcon && <i className={confirmIcon} />}
              {confirmLabel}
            </button>
          </div>
        </div>
      </div>
  );
}
