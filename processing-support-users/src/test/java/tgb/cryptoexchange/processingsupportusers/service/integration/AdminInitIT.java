package tgb.cryptoexchange.processingsupportusers.service.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tgb.cryptoexchange.processingsupportusers.dto.UserDTO;
import tgb.cryptoexchange.processingsupportusers.enums.UserRole;
import tgb.cryptoexchange.processingsupportusers.service.AdminInitializer;
import tgb.cryptoexchange.processingsupportusers.service.UserService;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@AutoConfigureMockMvc
class AdminInitIT extends BaseIntegrationTest {

    @Autowired
    private AdminInitializer adminInitializer;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Администратор должен создаваться, если база данных пуста")
    void initAdminUser_ShouldCreateAdmin_WhenDatabaseIsEmpty() {
        assertThat(userRepository.count()).isZero();

        adminInitializer.initAdminUser();

        var adminOptional = userRepository.findByUsername("admin");
        assertThat(adminOptional).isPresent();
        assertThat(adminOptional.get().getRole()).isEqualTo(UserRole.ADMINISTRATOR);

    }

    @Test
    @DisplayName("Администратор НЕ должен создаваться повторно, если в базе уже есть пользователи")
    void initAdminUser_ShouldNotCreateAdmin_WhenDatabaseIsNotEmpty() {
        userService.create(UserDTO.builder()
                .username("existing_user")
                .password("StrongPass123!")
                .build());

        long countBeforeEvent = userRepository.count();
        assertThat(countBeforeEvent).isEqualTo(1);

        adminInitializer.initAdminUser();

        assertThat(userRepository.count()).isEqualTo(countBeforeEvent);
        assertThat(userRepository.existsByUsername("admin")).isFalse();
    }

}
