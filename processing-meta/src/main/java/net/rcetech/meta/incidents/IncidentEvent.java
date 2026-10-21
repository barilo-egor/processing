package net.rcetech.meta.incidents;

public interface IncidentEvent {

    IncidentType type();

    long errorTimestamp();

    default boolean onlyIfNoUnresolved() {
        return false;
    }
}
