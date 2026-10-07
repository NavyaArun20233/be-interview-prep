package com.interviewprep.service;

import com.interviewprep.dto.auth.LoginRequest;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.entity.User;
import com.interviewprep.exception.InvalidCredentialsException;
import com.interviewprep.repository.UserRepository;
import com.interviewprep.validation.PasswordValidator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Hash of a random value, checked when the email is unknown so that a miss costs one password hash like a hit
     * does; otherwise response time would reveal which emails are registered.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Not {@code @Transactional}: a single repository read, and the deliberately slow password check should not hold a
     * database connection.
     */
    public TokenResponse login(LoginRequest request) {
        // bcrypt ignores bytes beyond 72, so such a password can never be the registered one.
        if (!PasswordValidator.fitsBcrypt(request.password())) {
            throw new InvalidCredentialsException();
        }
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            log.info("Login failed");
            throw new InvalidCredentialsException();
        }
        log.info("User {} logged in", user.get().getId());
        return tokenService.issue(
                user.get().getId(), user.get().getEmail(), user.get().getRole());
    }
}
