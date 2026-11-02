package com.microloan.Micro_loan.user.service;

import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public User getProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User updateProfile(String email, String name) {
        User user = getProfile(email);
        if (!user.getName().equals(name)) {
            user.setName(name);
            return userRepository.save(user);
        }
        return user;
    }

    public List<User> search(String query, String currentEmail) {
        return userRepository.findTop20ByNameContainingIgnoreCaseAndEmailNotOrderByNameAsc(query, currentEmail);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(), true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
