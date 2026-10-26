package com.krugworld.debt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Part (or all) of a debt paid back. */
@Entity
@Table(name = "debt_payment")
public class DebtPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "debt_id", nullable = false, updatable = false)
    private Long debtId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false, length = 200)
    private String note = "";

    protected DebtPayment() {
    }

    public DebtPayment(Long debtId, BigDecimal amount, LocalDate paidOn, String note) {
        this.debtId = debtId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getDebtId() {
        return debtId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getNote() {
        return note;
    }
}
