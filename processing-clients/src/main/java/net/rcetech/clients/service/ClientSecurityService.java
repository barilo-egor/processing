package net.rcetech.clients.service;

import net.rcetech.meta.clients.dto.UpdateClientDTO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class ClientSecurityService {

    public boolean canUpdate(UUID id, UpdateClientDTO dto, Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        boolean isSelf = authentication.getName().equals(id.toString());
        if (!isSelf) {
            return false;
        }
        return Objects.isNull(dto.status()) && Objects.isNull(dto.orderTimeoutSeconds())
                && Objects.isNull(dto.commissionPercent());
    }
}
