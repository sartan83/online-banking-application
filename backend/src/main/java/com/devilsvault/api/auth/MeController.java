package com.devilsvault.api.auth;

import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    public record MeResponse(String username, String role, boolean mfaEnabled) {
    }

    @GetMapping
    public MeResponse me(Principal principal) {
        User user = users.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new MeResponse(user.getUsername(), user.getRole().name(), user.isMfaEnabled());
    }
}
