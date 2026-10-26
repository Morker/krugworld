package com.krugworld.debt;

import com.krugworld.common.ApiException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Debts between the user and other people, paid back in parts. What's still
 * owed is the amount minus the repayments, summed in SQL each time.
 */
@Service
@Transactional
public class DebtService {

    public static final int DEBT_CLEARED_XP = 10;

    public record DebtRequest(
            @NotNull Debt.Direction direction,
            @NotBlank @Size(max = 80) String person,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @Size(max = 200) String note,
            /** When it started; defaults to today. */
            LocalDate startedOn,
            LocalDate dueOn) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            /** Defaults to today. */
            LocalDate paidOn,
            @Size(max = 200) String note) {
    }

    public record PaymentView(Long id, BigDecimal amount, LocalDate paidOn, String note) {
        static PaymentView of(DebtPayment p) {
            return new PaymentView(p.getId(), p.getAmount(), p.getPaidOn(), p.getNote());
        }
    }

    public record DebtView(Long id, Debt.Direction direction, String person, BigDecimal amount, BigDecimal repaid,
                           BigDecimal outstanding, String note, LocalDate startedOn, LocalDate dueOn,
                           LocalDate settledOn, boolean overdue, List<PaymentView> payments) {
    }

    private final DebtRepository debts;
    private final DebtPaymentRepository payments;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DebtService(DebtRepository debts, DebtPaymentRepository payments, JdbcTemplate jdbc,
                       ApplicationEventPublisher events, Clock clock) {
        this.debts = debts;
        this.payments = payments;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /** Open debts first (soonest due on top), then settled ones, most recent first. */
    @Transactional(readOnly = true)
    public List<DebtView> list(Long userId) {
        List<Debt> all = debts.findByUserId(userId);
        Map<Long, BigDecimal> repaid = repaidByDebt(userId);
        Map<Long, List<PaymentView>> paymentsByDebt = all.isEmpty() ? Map.of()
                : payments.findByDebtIdInOrderByPaidOnAscIdAsc(all.stream().map(Debt::getId).toList()).stream()
                .collect(Collectors.groupingBy(DebtPayment::getDebtId,
                        Collectors.mapping(PaymentView::of, Collectors.toList())));

        Comparator<Debt> open = Comparator.comparing(Debt::getDueOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Debt::getStartedOn)
                .thenComparing(Debt::getId);
        Comparator<Debt> settled = Comparator.comparing(Debt::getSettledOn).thenComparing(Debt::getId).reversed();
        Comparator<Debt> order = (a, b) -> a.isSettled() != b.isSettled()
                ? Boolean.compare(a.isSettled(), b.isSettled())
                : (a.isSettled() ? settled : open).compare(a, b);
        return all.stream().sorted(order)
                .map(d -> view(d, repaid.getOrDefault(d.getId(), BigDecimal.ZERO),
                        paymentsByDebt.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    public DebtView create(Long userId, DebtRequest req) {
        Debt debt = new Debt(userId);
        apply(debt, req);
        return view(debts.save(debt));
    }

    public DebtView update(Long userId, Long id, DebtRequest req) {
        Debt debt = locked(userId, id);
        apply(debt, req);
        BigDecimal repaid = repaid(debt.getId());
        if (req.amount().compareTo(repaid) < 0) {
            throw ApiException.badRequest("amount_below_repaid",
                    "RM " + repaid.toPlainString() + " has already been paid back, so it can't be less than that.");
        }
        settleIfPaidUp(debt, LocalDate.now(clock));
        return view(debt);
    }

    public void delete(Long userId, Long id) {
        debts.delete(debt(userId, id));
    }

    /** Record money paid back. Paying the last of it settles the debt. */
    public DebtView pay(Long userId, Long id, PaymentRequest req) {
        Debt debt = locked(userId, id);
        if (debt.isSettled()) {
            throw ApiException.conflict("already_settled", "That debt is already settled.");
        }
        BigDecimal outstanding = debt.getAmount().subtract(repaid(debt.getId()));
        if (req.amount().compareTo(outstanding) > 0) {
            throw ApiException.badRequest("more_than_owed", "Only RM " + outstanding.toPlainString() + " is left to pay.");
        }
        LocalDate paidOn = req.paidOn() == null ? LocalDate.now(clock) : req.paidOn();
        payments.save(new DebtPayment(debt.getId(), req.amount(), paidOn, req.note() == null ? "" : req.note().trim()));
        settleIfPaidUp(debt, paidOn);
        return view(debt);
    }

    /** Undo a repayment recorded by mistake; a debt that's owed again is open again. */
    public DebtView deletePayment(Long userId, Long id, Long paymentId) {
        Debt debt = locked(userId, id);
        payments.delete(payments.findByIdAndDebtId(paymentId, debt.getId())
                .orElseThrow(() -> ApiException.notFound("Repayment")));
        payments.flush();
        if (debt.getAmount().compareTo(repaid(debt.getId())) > 0) {
            debt.setSettledOn(null);
        }
        return view(debt);
    }

    /** Let the rest go: forgiven, written off, or settled some other way. No XP for this one. */
    public DebtView settle(Long userId, Long id) {
        Debt debt = locked(userId, id);
        if (!debt.isSettled()) {
            debt.setSettledOn(LocalDate.now(clock));
        }
        return view(debt);
    }

    public DebtView reopen(Long userId, Long id) {
        Debt debt = locked(userId, id);
        if (debt.getAmount().compareTo(repaid(debt.getId())) <= 0) {
            throw ApiException.conflict("fully_repaid", "That debt was paid back in full. Remove a repayment instead.");
        }
        debt.setSettledOn(null);
        return view(debt);
    }

    private void apply(Debt debt, DebtRequest req) {
        LocalDate startedOn = req.startedOn() == null
                ? (debt.getStartedOn() == null ? LocalDate.now(clock) : debt.getStartedOn())
                : req.startedOn();
        if (req.dueOn() != null && req.dueOn().isBefore(startedOn)) {
            throw ApiException.badRequest("due_before_start", "The due date can't be before the debt started.");
        }
        debt.setDirection(req.direction());
        debt.setPerson(req.person().trim());
        debt.setAmount(req.amount());
        debt.setNote(req.note() == null ? "" : req.note().trim());
        debt.setStartedOn(startedOn);
        debt.setDueOn(req.dueOn());
    }

    private void settleIfPaidUp(Debt debt, LocalDate on) {
        if (!debt.isSettled() && debt.getAmount().compareTo(repaid(debt.getId())) <= 0) {
            debt.setSettledOn(on);
            events.publishEvent(new DebtCleared(debt.getUserId(), debt.getId()));
        }
    }

    private BigDecimal repaid(Long debtId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM debt_payment WHERE debt_id = ?",
                BigDecimal.class, debtId);
    }

    private Map<Long, BigDecimal> repaidByDebt(Long userId) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        jdbc.query("""
                SELECT p.debt_id, SUM(p.amount) AS repaid
                FROM debt_payment p JOIN debt d ON d.id = p.debt_id
                WHERE d.user_id = ?
                GROUP BY p.debt_id
                """, rs -> {
            totals.put(rs.getLong("debt_id"), rs.getBigDecimal("repaid"));
        }, userId);
        return totals;
    }

    private DebtView view(Debt d) {
        List<PaymentView> list = payments.findByDebtIdInOrderByPaidOnAscIdAsc(List.of(d.getId())).stream()
                .map(PaymentView::of).toList();
        return view(d, repaid(d.getId()), list);
    }

    private DebtView view(Debt d, BigDecimal repaid, List<PaymentView> list) {
        BigDecimal outstanding = d.isSettled() ? BigDecimal.ZERO : d.getAmount().subtract(repaid).max(BigDecimal.ZERO);
        boolean overdue = !d.isSettled() && d.getDueOn() != null && d.getDueOn().isBefore(LocalDate.now(clock));
        return new DebtView(d.getId(), d.getDirection(), d.getPerson(), d.getAmount(), repaid, outstanding, d.getNote(),
                d.getStartedOn(), d.getDueOn(), d.getSettledOn(), overdue, list);
    }

    private Debt debt(Long userId, Long id) {
        return debts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Debt"));
    }

    private Debt locked(Long userId, Long id) {
        return debts.lockByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Debt"));
    }
}
