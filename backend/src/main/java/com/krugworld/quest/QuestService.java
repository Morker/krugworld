package com.krugworld.quest;

import com.krugworld.common.ApiException;
import com.krugworld.ledger.LedgerDtos;
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
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class QuestService {

    public static final int QUEST_XP = 200;

    public record QuestRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank String icon,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
            LocalDate deadline) {
    }

    /** Positive to put money towards the quest, negative to take some back. */
    public record ContributionRequest(@NotNull @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record QuestView(Long id, String name, String icon, BigDecimal targetAmount, BigDecimal savedAmount,
                            LocalDate deadline, OffsetDateTime completedAt, int xpReward) {
        static QuestView of(Goal g) {
            return new QuestView(g.getId(), g.getName(), g.getIcon(), g.getTargetAmount(), g.getSavedAmount(),
                    g.getDeadline(), g.getCompletedAt(), QUEST_XP);
        }
    }

    private final GoalRepository goals;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QuestService(GoalRepository goals, ApplicationEventPublisher events, Clock clock) {
        this.goals = goals;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<QuestView> list(Long userId) {
        return goals.findByUserIdOrderByCompletedAtDescIdDesc(userId).stream().map(QuestView::of).toList();
    }

    public QuestView create(Long userId, QuestRequest req) {
        Goal goal = new Goal(userId);
        apply(goal, req);
        return QuestView.of(goals.save(goal));
    }

    public QuestView update(Long userId, Long id, QuestRequest req) {
        Goal goal = goal(userId, id);
        apply(goal, req);
        checkCompletion(goal);
        return QuestView.of(goal);
    }

    public void delete(Long userId, Long id) {
        goals.delete(goal(userId, id));
    }

    public QuestView contribute(Long userId, Long id, ContributionRequest req) {
        if (req.amount().signum() == 0) {
            throw ApiException.badRequest("zero_amount", "Enter an amount to add or take back.");
        }
        Goal goal = goal(userId, id);
        goal.setSavedAmount(goal.getSavedAmount().add(req.amount()).max(BigDecimal.ZERO));
        checkCompletion(goal);
        return QuestView.of(goal);
    }

    /** Once complete, a quest stays complete — the reward has been given. */
    private void checkCompletion(Goal goal) {
        if (!goal.isComplete() && goal.getSavedAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setCompletedAt(OffsetDateTime.now(clock));
            events.publishEvent(new QuestCompleted(goal.getUserId(), goal.getId()));
        }
    }

    private void apply(Goal goal, QuestRequest req) {
        if (!LedgerDtos.ICONS.contains(req.icon())) {
            throw ApiException.badRequest("unknown_icon", "Unknown icon: " + req.icon());
        }
        goal.setName(req.name().trim());
        goal.setIcon(req.icon());
        goal.setTargetAmount(req.targetAmount());
        goal.setDeadline(req.deadline());
    }

    private Goal goal(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Quest"));
    }
}
