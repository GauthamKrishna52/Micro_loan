package com.microloan.Micro_loan.balance.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "balance_refresh_lock")
public class BalanceRefreshLock {

    @Id
    private Integer id;

    protected BalanceRefreshLock() { }

    public BalanceRefreshLock(Integer id) { this.id = id; }
    public Integer getId() { return id; }
}
