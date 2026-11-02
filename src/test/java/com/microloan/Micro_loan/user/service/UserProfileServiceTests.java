package com.microloan.Micro_loan.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTests {
    @Mock UserRepository repository;
    @InjectMocks UserService service;

    @Test
    void retrievesExistingProfile() {
        User user = user("alice@example.com", "Alice");
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        assertEquals("Alice", service.getProfile("alice@example.com").getName());
    }

    @Test
    void updatesOnlyName() {
        User user = user("alice@example.com", "Alice");
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        User result = service.updateProfile("alice@example.com", "Alicia");
        assertEquals("Alicia", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertNotNull(result.getPassword());
        verify(repository).save(user);
    }

    @Test
    void missingUserIsNotFoundAndDoesNotWrite() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getProfile("missing@example.com"));
        verify(repository, never()).save(any());
    }

    private static User user(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword("hashed");
        return user;
    }
}
