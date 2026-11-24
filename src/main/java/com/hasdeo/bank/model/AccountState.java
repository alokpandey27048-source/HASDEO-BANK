package com.hasdeo.bank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "demo_account_state")
public class AccountState {
    @Id
    private Long id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    protected AccountState() {}

    public AccountState(Long id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
