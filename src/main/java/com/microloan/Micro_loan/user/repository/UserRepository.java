package com.microloan.Micro_loan.user.repository;

import com.microloan.Micro_loan.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findTop20ByNameContainingIgnoreCaseAndEmailNotOrderByNameAsc(String name, String email);
}
