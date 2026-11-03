package com.microloan.Micro_loan.balance.repository;

import com.microloan.Micro_loan.balance.entity.BalanceSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, UUID> {
    Optional<BalanceSnapshot> findByUserId(UUID userId);
    List<BalanceSnapshot> findAllByUserIdIn(Collection<UUID> userIds);
}
