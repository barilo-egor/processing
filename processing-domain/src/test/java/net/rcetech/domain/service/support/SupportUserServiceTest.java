package net.rcetech.domain.service.support;

import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.support.SupportUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(SupportUserService.class)
class SupportUserServiceTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
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