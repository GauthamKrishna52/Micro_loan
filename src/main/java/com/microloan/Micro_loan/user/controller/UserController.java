package com.microloan.Micro_loan.user.controller;

import com.microloan.Micro_loan.user.dto.ProfileResponse;
import com.microloan.Micro_loan.user.dto.ProfileUpdateRequest;
import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ProfileResponse getMe(Authentication authentication) {
        return toResponse(userService.getProfile(currentEmail(authentication)));
    }

    /** PUT performs a full profile update for the permitted profile field, name. */
    @PutMapping("/me")
    public ProfileResponse updateMe(Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) {
        return toResponse(userService.updateProfile(currentEmail(authentication), request.name().trim()));
    }

    @GetMapping("/search")
    public List<ProfileResponse> search(Authentication authentication, @RequestParam("q") String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must contain 1 to 80 characters");
        }
        return userService.search(normalized, currentEmail(authentication)).stream().map(UserController::toResponse).toList();
    }

    private static String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName();
    }

    private static ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getId(), user.getName(), user.getCreatedAt());
    }
}
