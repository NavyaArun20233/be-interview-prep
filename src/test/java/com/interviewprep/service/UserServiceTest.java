package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.auth.RegisterRequest;
import com.interviewprep.dto.auth.UserResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.entity.User;
import com.interviewprep.exception.EmailAlreadyRegisteredException;
import com.interviewprep.exception.UserNotFoundException;
import com.interviewprep.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String PASSWORD = "correct horse battery";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, CLOCK);
    }

    @Test
    void registerStoresLowerCasedEmailBcryptHashAndUserRole() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.register(new RegisterRequest(" Ada@Example.COM ", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByEmail("ada@example.com");
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void registerRejectsEmailThatIsAlreadyRegisteredInAnyCase() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest("ADA@example.com", PASSWORD)))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("An account with this email already exists");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesConcurrentDuplicateInsertToConflict() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> userService.register(new RegisterRequest("ada@example.com", PASSWORD)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void createAdminIfAbsentCreatesAdminWhenEmailIsFree() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = userService.createAdminIfAbsent(" Admin@Example.com", PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(created).isTrue();
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
    }

    @Test
    void createAdminIfAbsentLeavesExistingAccountUnchanged() {
        User existing = new User("admin@example.com", "{bcrypt}hash", Role.USER, NOW);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        boolean created = userService.createAdminIfAbsent("admin@example.com", PASSWORD);

        assertThat(created).isFalse();
        assertThat(existing.getRole()).isEqualTo(Role.USER);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void getReturnsProfile() {
        when(userRepository.findById(7L))
                .thenReturn(Optional.of(new User("ada@example.com", "{bcrypt}hash", Role.USER, NOW)));

        assertThat(userService.get(7L).email()).isEqualTo("ada@example.com");
    }

    @Test
    void getThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(42L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User 42 not found");
    }

    @Test
    void listReturnsProfilesSortedById() {
        User admin = new User("admin@example.com", "{bcrypt}hash", Role.ADMIN, NOW);
        Pageable expected = PageRequest.of(1, 5, Sort.by("id"));
        when(userRepository.findAll(expected)).thenReturn(new PageImpl<>(List.of(admin), expected, 6));

        PageResponse<UserResponse> page = userService.list(1, 5);

        assertThat(page.content()).extracting(UserResponse::email).containsExactly("admin@example.com");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
    }
}
