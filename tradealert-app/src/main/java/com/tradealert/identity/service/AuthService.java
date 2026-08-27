package com.tradealert.identity.service;

import com.tradealert.identity.model.User;
import com.tradealert.identity.repository.UserRepository;
import com.tradealert.identity.security.JwtUtil;
import com.tradealert.verification.service.VerificationService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final VerificationService verificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil,
            RedisTemplate<String, String> redisTemplate,
            VerificationService verificationService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.verificationService = verificationService;
    }

    @Transactional
    public String register(User user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setVerified(false);
        user.setCreatedAt(Instant.now());
        User saved = userRepository.save(user);
        verificationService.sendVerification(saved);
        return jwtUtil.generateToken(saved.getId().toString());
    }

    public String login(String email, String password, String device, String location) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId().toString());
        redisTemplate.opsForValue().set("session:" + user.getId(), token);
        if (location != null) {
            redisTemplate.opsForValue().set("login:last:" + user.getId(), location);
        }
        return token;
    }

    public void logout(Long userId) {
        redisTemplate.delete("session:" + userId);
        redisTemplate.delete("login:failures:" + userId);
    }
}
