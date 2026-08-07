package tgb.cryptoexchange.processingsupportusers.service.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tgb.cryptoexchange.processingsupportusers.dto.UserDTO;
import tgb.cryptoexchange.processingsupportusers.entity.SupportUser;
import tgb.cryptoexchange.processingsupportusers.enums.UserRole;
import tgb.cryptoexchange.processingsupportusers.exceptions.NotFoundException;
import tgb.cryptoexchange.processingsupportusers.exceptions.PasswordValidationException;
import tgb.cryptoexchange.processingsupportusers.exceptions.UserAlreadyExistsException;
import tgb.cryptoexchange.processingsupportusers.exceptions.UserNotFoundException;
import tgb.cryptoexchange.processingsupportusers.mapper.UserMapper;
import tgb.cryptoexchange.processingsupportusers.repository.UserRepository;
import tgb.cryptoexchange.processingsupportusers.service.UserService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserDTO testDto;

    private SupportUser testEntity;

    @BeforeEach
    void setUp() {
        testDto = UserDTO.builder()
                .username("testUser")
                .password("Valid123@$")
                .role(UserRole.ADMINISTRATOR)
                .build();

        testEntity = SupportUser.builder()
                .id(1L)
                .username("testUser")
                .password("encodedPassword")
                .build();
    }

    @Test
    @DisplayName("Успешное создание пользователя")
    void create_Success() {
        when(userRepository.existsByUsername(testDto.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(testDto.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(SupportUser.class))).thenReturn(testEntity);
        when(userMapper.fromEntity(testEntity)).thenReturn(testDto);

        UserDTO result = userService.create(testDto);

        assertNotNull(result);
        assertEquals(testDto.getUsername(), result.getUsername());
        verify(userRepository).existsByUsername(testDto.getUsername());
        verify(passwordEncoder).encode(testDto.getPassword());
        verify(userRepository).save(any(SupportUser.class));
        verify(userMapper).fromEntity(testEntity);
    }

    @Test
    @DisplayName("Выброс исключения, если имя пользователя уже занято")
    void create_ThrowsUserAlreadyExistsException() {
        when(userRepository.existsByUsername(testDto.getUsername())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.create(testDto));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Выброс исключения, если пароль null")
    void create_ThrowsPasswordValidationException_WhenPasswordIsNull() {
        UserDTO dtoWithNullPassword = UserDTO.builder()
                .username("testUser")
                .password(null)
                .build();
        when(userRepository.existsByUsername(dtoWithNullPassword.getUsername())).thenReturn(false);

        assertThrows(PasswordValidationException.class, () -> userService.create(dtoWithNullPassword));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Выброс исключения, если пароль слишком короткий (< 8 символов)")
    void create_ThrowsPasswordValidationException_WhenPasswordTooShort() {
        UserDTO shortPasswordDto = UserDTO.builder()
                .username("testUser")
                .password("Sh1@")
                .build();
        when(userRepository.existsByUsername(shortPasswordDto.getUsername())).thenReturn(false);

        assertThrows(PasswordValidationException.class, () -> userService.create(shortPasswordDto));
    }

    @Test
    @DisplayName("Выброс исключения, если в пароле нет спецсимволов")
    void create_ThrowsPasswordValidationException_WhenNoSpecialChars() {
        UserDTO noSpecDto = UserDTO.builder()
                .username("testUser")
                .password("NoSpecial123")
                .build();
        when(userRepository.existsByUsername(noSpecDto.getUsername())).thenReturn(false);

        assertThrows(PasswordValidationException.class, () -> userService.create(noSpecDto));

    }

    @Test
    @DisplayName("Успешный поиск пользователя по имени")
    void getUserByUsername_Success() {
        String username = "testUser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testEntity));
        when(userMapper.fromEntity(testEntity)).thenReturn(testDto);

        UserDTO result = userService.getUserByUsername(username);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
        verify(userRepository).findByUsername(username);
    }

    @Test
    @DisplayName("Выброс NotFoundException, если пользователь не найден")
    void getUserByUsername_ThrowsNotFoundException() {
        String username = "unknownUser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserByUsername(username));

    }

    @Test
    @DisplayName("Успешный поиск пользователя по ID")
    void getUserById_Success() {
        Long id = 1L;
        when(userRepository.findSupportUserById(id)).thenReturn(Optional.of(testEntity));
        when(userMapper.fromEntity(testEntity)).thenReturn(testDto);

        UserDTO result = userService.getUserById(id);

        assertNotNull(result);
        verify(userRepository).findSupportUserById(id);
    }

    @Test
    @DisplayName("Выброс UserNotFoundException, если ID не существует")
    void getUserById_ThrowsUserNotFoundException() {
        // Given
        Long id = 999L;
        when(userRepository.findSupportUserById(id)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(id));
    }

}