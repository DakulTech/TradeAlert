package com.tradealert.userservice.controller;

import com.tradealert.userservice.model.User;
import com.tradealert.userservice.service.AuthService;
import com.tradealert.userservice.route.UserRoutes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.BASE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        String token = authService.register(user);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String location) {
        String token = authService.login(email, password, device, location);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout/{userId}")
    public ResponseEntity<Void> logout(@PathVariable Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
