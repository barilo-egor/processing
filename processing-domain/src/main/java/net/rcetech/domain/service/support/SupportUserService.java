package net.rcetech.domain.service.support;

import net.rcetech.domain.repository.support.SupportUserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SupportUserService {

    private final SupportUserRepository supportUserRepository;

    public SupportUserService(SupportUserRepository supportUserRepository) {
        this.supportUserRepository = supportUserRepository;
    }

    public boolean isExists(UUID id) {
        return supportUserRepository.existsById(id);
    }

}
