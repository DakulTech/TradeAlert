package com.tradealert.verification.controller;

import com.tradealert.verification.service.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verify")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/confirm")
    public ResponseEntity<String> confirmVerification(@RequestParam Long userId,
            @RequestParam String token) {
        if (verificationService.confirmVerification(userId, token)) {
            return ResponseEntity.ok("Verification successful for user " + userId);
        }
        return ResponseEntity.badRequest().body("Invalid or expired verification token");
    }
}
