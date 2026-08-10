package net.rcetech.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.rcetech.support.dto.UserDTO;
import net.rcetech.support.entity.SupportUser;
import net.rcetech.support.exceptions.NotFoundException;
import net.rcetech.support.exceptions.PasswordValidationException;
import net.rcetech.support.exceptions.UserAlreadyExistsException;
import net.rcetech.support.exceptions.UserNotFoundException;
import net.rcetech.support.mapper.UserMapper;
import net.rcetech.support.repository.UserRepository;

@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private static final String STRENGTH_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    public UserService(PasswordEncoder passwordEncoder, UserRepository userRepository, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Создает нового пользователя.
     * Метод хэширует пароль, сохраняет сущность в базу данных.
     *
     * @param userDTO данные для создания нового пользователя
     * @throws UserAlreadyExistsException  если пользователь с таким username уже зарегистрирован
     * @throws PasswordValidationException если пароль не прошел валидацию
     */
    @Transactional
    public UserDTO create(UserDTO userDTO) {
        log.debug("Запрос на создание пользователя: username {}", userDTO.getUsername());
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new UserAlreadyExistsException();
        }
        final String encryptedPassword = validateAndHashPassword(userDTO.getPassword());
        SupportUser user = SupportUser.builder().username(userDTO.getUsername()).password(encryptedPassword).build();
        user = userRepository.save(user);
        log.debug("Создан пользователь: {}", user.getId());
        return userMapper.fromEntity(user);
    }

    /**
     * Возвращает данные пользователя по его username.
     *
     * @param username имя пользователя для поиска
     * @return {@link UserDTO} с данными найденного клиента
     * @throws NotFoundException если клиент с указанным username не найден в системе
     */
    public UserDTO getUserByUsername(String username) {
        log.debug("Запрос пользователя: username {}", username);
        SupportUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(username));
        return userMapper.fromEntity(user);
    }

    /**
     * Возвращает данные пользователя по его ID.
     *
     * @param id уникальный идентификатор пользователя
     * @return {@link UserDTO} с данными найденного пользователя
     * @throws UserNotFoundException если пользователь с указанным ID не найден в системе
     */
    public UserDTO getUserById(Long id) {
        log.debug("Запрос user: id {}", id);
        SupportUser user = userRepository.findSupportUserById(id)
                .orElseThrow(UserNotFoundException::new);
        return userMapper.fromEntity(user);
    }

    /**
     * Проверяет надежность пароля по регулярному выражению и хэширует его.
     *
     * @param password исходный пароль в открытом виде
     * @return захэшированная строка пароля
     * @throws PasswordValidationException если пароль равен null или не соответствует требованиям безопасности
     */
    public String validateAndHashPassword(String password) {
        if (password == null || !password.matches(STRENGTH_REGEX)) {
            throw new PasswordValidationException();
        }
        return passwordEncoder.encode(password);
    }

}
