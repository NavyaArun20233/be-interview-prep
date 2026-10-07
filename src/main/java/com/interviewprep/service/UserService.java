package com.interviewprep.service;

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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Sort DEFAULT_SORT = Sort.by("id");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Self-registration always creates a {@link Role#USER}; admins are only created by the startup bootstrap. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = create(request.email(), request.password(), Role.USER);
        log.info("Registered user {}", user.getId());
        return UserResponse.from(user);
    }

    /**
     * Creates an ADMIN with the given credentials unless an account with that email already exists (which is left
     * unchanged, whatever its role). Returns whether an account was created.
     */
    @Transactional
    public boolean createAdminIfAbsent(String email, String rawPassword) {
        Optional<User> existing = userRepository.findByEmail(User.normalizeEmail(email));
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                log.warn(
                        "Admin bootstrap: user {} already exists with role {}; role not changed",
                        existing.get().getId(),
                        existing.get().getRole());
            }
            return false;
        }
        create(email, rawPassword, Role.ADMIN);
        return true;
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** ADMIN only; also enforced by the security filter chain, this is defense in depth for other callers. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        return PageResponse.from(userRepository.findAll(PageRequest.of(page, size, DEFAULT_SORT)), UserResponse::from);
    }

    private User create(String email, String rawPassword, Role role) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), role, now());
        try {
            // Flush so a concurrent registration of the same email hits the unique constraint here, not at commit.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
