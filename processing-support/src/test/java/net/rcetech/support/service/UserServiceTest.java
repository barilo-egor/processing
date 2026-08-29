package net.rcetech.support.service;

import net.rcetech.domain.mapper.support.UserMapper;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.support.SupportUserRepository;
import net.rcetech.meta.exception.NotFoundException;
import net.rcetech.meta.support.dto.UserDTO;
import net.rcetech.meta.support.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SupportUserRepository supportUserRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private net.rcetech.domain.service.support.UserService userService;

    private UserDTO testDto;

    private SupportUser testEntity;

    @BeforeEach
    void setUp() {
        testDto = UserDTO.builder()
                .username("testUser")
                .build();

        testEntity = SupportUser.builder()
                .id(1L)
                .username("testUser")
                .build();
    }

    @Test
    @DisplayName("Успешный поиск пользователя по имени")
    void getUserByUsername_Success() {
        String username = "testUser";
        when(supportUserRepository.findByUsername(username)).thenReturn(Optional.of(testEntity));
        when(userMapper.fromEntity(testEntity)).thenReturn(testDto);

        UserDTO result = userService.getUserByUsername(username);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
        verify(supportUserRepository).findByUsername(username);
    }

    @Test
    @DisplayName("Выброс NotFoundException, если пользователь не найден")
    void getUserByUsername_ThrowsNotFoundException() {
        String username = "unknownUser";
        when(supportUserRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserByUsername(username));

    }

    @Test
    @DisplayName("Успешный поиск пользователя по ID")
    void getUserById_Success() {
        Long id = 1L;
        when(supportUserRepository.findSupportUserById(id)).thenReturn(Optional.of(testEntity));
        when(userMapper.fromEntity(testEntity)).thenReturn(testDto);

        UserDTO result = userService.getUserById(id);

        assertNotNull(result);
        verify(supportUserRepository).findSupportUserById(id);
    }

    @Test
    @DisplayName("Выброс UserNotFoundException, если ID не существует")
    void getUserById_ThrowsUserNotFoundException() {
        // Given
        Long id = 999L;
        when(supportUserRepository.findSupportUserById(id)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(id));
    }

}