package net.rcetech.domain.service.support;

import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.support.SupportUserRepository;
import net.rcetech.domain.test.SharedMySQLContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SupportUserService.class)
class SupportUserServiceTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private SupportUserRepository supportUserRepository;

    @Autowired
    private SupportUserService supportUserService;

    @ParameterizedTest
    @CsvSource({
            "f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92,test1",
            "80adcfbe-d80d-432a-82d3-b61bcc7aacdd,smokilolik"
    })
    @DisplayName("Метод не должен создавать клиента, если клиент с переданным id уже существует.")
    void createIfNotExists_shouldNotCreateClientIfExists(UUID id, String username) {
        SupportUser user = new SupportUser();
        user.setId(id);
        user.setUsername(username);
        user.setRegisteredAt(Instant.now());
        supportUserRepository.save(user);

        supportUserService.createIfNotExists(id, username);

        assertEquals(1, supportUserRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92,test1",
            "80adcfbe-d80d-432a-82d3-b61bcc7aacdd,smokilolik"
    })
    @DisplayName("Метод не должен создавать клиента, если клиент с переданным id уже существует.")
    void createIfNotExists_shouldCreateClientIfNotExists(UUID id, String username) {
        assertEquals(0, supportUserRepository.count());

        supportUserService.createIfNotExists(id, username);

        List<SupportUser> actual = supportUserRepository.findAll();
        assertEquals(1, actual.size());
        assertEquals(id, actual.getFirst().getId());
        assertEquals(username, actual.getFirst().getUsername());
    }
}