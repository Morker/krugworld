package com.krugworld.recurring;

import com.krugworld.ledger.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "recurring_rule")
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerEntry.Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String note = "";

    @Column(name = "day_of_month", nullable = false)
    private short dayOfMonth;

    @Column(name = "next_run_on", nullable = false)
    private LocalDate nextRunOn;

    @Column(nullable = false)
    private boolean active = true;

    protected RecurringRule() {
    }

    public RecurringRule(Long userId) {
        this.userId = userId;
    }

    /** This rule's date within a month, pulling 29–31 back to the last day of shorter months. */
    public LocalDate dateIn(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    /** The first date on or after {@code from} that this rule falls on. */
    public LocalDate firstRunOnOrAfter(LocalDate from) {
        LocalDate candidate = dateIn(YearMonth.from(from));
        return candidate.isBefore(from) ? dateIn(YearMonth.from(from).plusMonths(1)) : candidate;
    }

    public void advance() {
        nextRunOn = dateIn(YearMonth.from(nextRunOn).plusMonths(1));
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LedgerEntry.Type getType() {
        return type;
    }

    public void setType(LedgerEntry.Type type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = (short) dayOfMonth;
    }

    public LocalDate getNextRunOn() {
        return nextRunOn;
    }

    public void setNextRunOn(LocalDate nextRunOn) {
        this.nextRunOn = nextRunOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
