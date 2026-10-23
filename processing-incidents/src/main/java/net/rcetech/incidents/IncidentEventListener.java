package net.rcetech.incidents;

import net.rcetech.incidents.handler.IncidentHandler;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.IncidentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentEventListener {

    private final List<IncidentHandler> handlers;

    public IncidentEventListener(List<IncidentHandler> handlers) {
        this.handlers = handlers;
    }

    @EventListener
    public void incident(IncidentEvent event) {
        handlers.stream()
                .filter(handler -> handler.supports(event))
                .findFirst()
                .orElseThrow(() -> new BaseException("Не найден обработчик инцидента " + event.type() + "."))
                .handle(event);
    }
}
