package net.rcetech.meta.clients;

import java.util.List;

public record IpAddressesChangedEvent(
        List<String> addresses
) {}
