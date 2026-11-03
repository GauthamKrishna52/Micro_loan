package com.microloan.Micro_loan.balance.repository;

import com.microloan.Micro_loan.balance.entity.BalanceRefreshLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BalanceRefreshLockRepository extends JpaRepository<BalanceRefreshLock, Integer> {

    @Modifying
    @Query(value = "insert into balance_refresh_lock (id) values (1) on conflict (id) do nothing", nativeQuery = true)
    void ensureSingletonExists();

    @Query(value = "select id from balance_refresh_lock where id = 1 for update", nativeQuery = true)
    Integer lockSingleton();

    default void acquire() {
        ensureSingletonExists();
        lockSingleton();
    }
}
