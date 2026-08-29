package net.rcetech.domain.service.support;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.mapper.support.UserMapper;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.support.SupportUserRepository;
import net.rcetech.meta.exception.NotFoundException;
import net.rcetech.meta.support.dto.UserDTO;
import net.rcetech.meta.support.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final SupportUserRepository supportUserRepository;

    private final UserMapper userMapper;

    public UserService(SupportUserRepository supportUserRepository, UserMapper userMapper) {
        this.supportUserRepository = supportUserRepository;
        this.userMapper = userMapper;
    }

    /**
     * Возвращает данные пользователя по его username.
     *
     * @param username имя пользователя для поиска
     * @return {@link net.rcetech.meta.support.dto.UserDTO} с данными найденного клиента
     * @throws NotFoundException если клиент с указанным username не найден в системе
     */
    public UserDTO getUserByUsername(String username) {
        log.debug("Запрос пользователя: username {}", username);
        SupportUser user = supportUserRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(username));
        return userMapper.fromEntity(user);
    }

    /**
     * Возвращает данные пользователя по его ID.
     *
     * @param id уникальный идентификатор пользователя
     * @return {@link net.rcetech.meta.support.dto.UserDTO} с данными найденного пользователя
     * @throws UserNotFoundException если пользователь с указанным ID не найден в системе
     */
    public UserDTO getUserById(Long id) {
        log.debug("Запрос user: id {}", id);
        SupportUser user = supportUserRepository.findSupportUserById(id)
                .orElseThrow(UserNotFoundException::new);
        return userMapper.fromEntity(user);
    }

}
