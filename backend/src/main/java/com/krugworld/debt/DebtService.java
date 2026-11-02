package com.krugworld.debt;

import com.krugworld.common.ApiException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Debts between the user and other people: written down, then ticked off
 * once settled. Ticking pays XP the first time only; unticking reopens it.
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

    public record DebtView(Long id, Debt.Direction direction, String person, BigDecimal amount, String note,
                           LocalDate startedOn, LocalDate dueOn, LocalDate settledOn, boolean overdue) {
    }

    private final DebtRepository debts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DebtService(DebtRepository debts, ApplicationEventPublisher events, Clock clock) {
        this.debts = debts;
        this.events = events;
        this.clock = clock;
    }

    /** Open debts first (soonest due on top), then settled ones, most recent first. */
    @Transactional(readOnly = true)
    public List<DebtView> list(Long userId) {
        Comparator<Debt> open = Comparator.comparing(Debt::getDueOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Debt::getStartedOn)
                .thenComparing(Debt::getId);
        Comparator<Debt> settled = Comparator.comparing(Debt::getSettledOn).thenComparing(Debt::getId).reversed();
        Comparator<Debt> order = (a, b) -> a.isSettled() != b.isSettled()
                ? Boolean.compare(a.isSettled(), b.isSettled())
                : (a.isSettled() ? settled : open).compare(a, b);
        return debts.findByUserId(userId).stream().sorted(order).map(this::view).toList();
    }

    /** Only the ones still owed, in the same order as {@link #list}. */
    @Transactional(readOnly = true)
    public List<DebtView> open(Long userId) {
        return list(userId).stream().filter(d -> d.settledOn() == null).toList();
    }

    public DebtView create(Long userId, DebtRequest req) {
        Debt debt = new Debt(userId);
        apply(debt, req);
        return view(debts.save(debt));
    }

    public DebtView update(Long userId, Long id, DebtRequest req) {
        Debt debt = debt(userId, id);
        apply(debt, req);
        return view(debt);
    }

    public void delete(Long userId, Long id) {
        debts.delete(debt(userId, id));
    }

    /**
     * Tick it off: paid back, or no longer owed. Ticking an already settled
     * debt changes nothing. The XP is keyed on the debt, so unticking and
     * ticking again can't earn it twice.
     */
    public DebtView settle(Long userId, Long id) {
        Debt debt = debt(userId, id);
        if (!debt.isSettled()) {
            debt.setSettledOn(LocalDate.now(clock));
            events.publishEvent(new DebtCleared(userId, debt.getId()));
        }
        return view(debt);
    }

    /** Untick: it's owed again after all. */
    public DebtView reopen(Long userId, Long id) {
        Debt debt = debt(userId, id);
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

    private DebtView view(Debt d) {
        boolean overdue = !d.isSettled() && d.getDueOn() != null && d.getDueOn().isBefore(LocalDate.now(clock));
        return new DebtView(d.getId(), d.getDirection(), d.getPerson(), d.getAmount(), d.getNote(),
                d.getStartedOn(), d.getDueOn(), d.getSettledOn(), overdue);
    }

    private Debt debt(Long userId, Long id) {
        return debts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Debt"));
    }
}
