package net.rcetech.meta.incidents;

public record CloudflareIpSyncErrorEvent(
        String address,
        String action,
        String error,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.CLOUDFLARE_IP_SYNC_ERROR;
    }
}
