package net.rcetech.domain.service.support;

import jakarta.transaction.Transactional;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.model.support.SupportUser_;
import net.rcetech.domain.repository.support.SupportUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupportUserService {

    private final SupportUserRepository supportUserRepository;

    public SupportUserService(SupportUserRepository supportUserRepository) {
        this.supportUserRepository = supportUserRepository;
    }

    public <T> Optional<T> findById(UUID id, Class<T> projectionType) {
        return supportUserRepository.findBy(
                (from, criteriaBuilder) -> criteriaBuilder.equal(from.get(SupportUser_.id), id),
                query -> query.as(projectionType).one());
    }

    @Transactional
    public void createIfNotExists(UUID id, String username) {
        if (supportUserRepository.existsById(id)) {
            return;
        }
        SupportUser supportUser = new SupportUser();
        supportUser.setId(id);
        supportUser.setRegisteredAt(Instant.now());
        supportUser.setUsername(username);
        supportUserRepository.save(supportUser);
    }

}
