package tgb.cryptoexchange.processingsupportusers.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.processingsupportusers.entity.SupportUser;
import tgb.cryptoexchange.processingsupportusers.enums.UserRole;
import tgb.cryptoexchange.processingsupportusers.repository.UserRepository;
import tgb.cryptoexchange.processingsupportusers.util.PasswordGenerator;

@Component
@Slf4j
public class AdminInitializer {

    private final UserRepository userRepository;

    private final UserService userService;

    public AdminInitializer(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /**
     * Автоматически создает учетную запись администратора со сгенерированным паролем
     * при первом запуске приложения, если база данных пользователей пуста.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initAdminUser() {
        if (userRepository.count() == 0) {
            log.info("База данных пуста. Создание администратора...");
            String rawPassword = PasswordGenerator.generateValidPassword();
            try {
                SupportUser user = SupportUser.builder().username("admin")
                        .password(userService.validateAndHashPassword(rawPassword))
                        .role(UserRole.ADMINISTRATOR).build();
                userRepository.save(user);
                log.info("==================================================");
                log.info("СОЗДАН АДМИНИСТРАТОР ПО УМОЛЧАНИЮ!");
                log.info("Логин: {}", user.getUsername());
                log.info("Пароль: {}", rawPassword);
                log.info("==================================================");

            } catch (Exception e) {
                log.error("Ошибка при автоматическом создании администратора", e);
            }
        }
    }

}
