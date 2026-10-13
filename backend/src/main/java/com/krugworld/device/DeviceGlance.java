package com.krugworld.device;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.budget.BudgetService.MonthBudget;
import com.krugworld.game.CharacterService;
import com.krugworld.game.CharacterService.CharacterSheet;
import com.krugworld.ledger.LedgerDtos.EntryView;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerQueries;
import com.krugworld.life.LifeService;
import com.krugworld.life.LifeService.Today;
import com.krugworld.life.LifeService.TodayWorkout;
import com.krugworld.steward.StewardUsage;
import com.krugworld.vitals.VitalsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Everything the desk device's pages show, in one small, flat response.
 * The device parses it with ArduinoJson in a fixed amount of memory, so
 * lists are capped at three and names come pre-chosen (a category's name,
 * not its id).
 */
@Service
public class DeviceGlance {

    public record BudgetGlance(BigDecimal left, BigDecimal total, int daysLeft,
                               String tightest, BigDecimal tightestLeft) {
    }

    public record BudgetRow(String name, BigDecimal spent, BigDecimal budget, BigDecimal left) {
    }

    public record EventGlance(LocalDate date, String time, String title) {
    }

    public record QuestGlance(String name, BigDecimal saved, BigDecimal target) {
    }

    /** A loot-log line: "expense", "income" or "transfer", and what it was. */
    public record LootGlance(LocalDate date, String type, BigDecimal amount, String label) {
    }

    public record Summary(LocalDate date, String username, int level, String title, int xp, int levelXp,
                          int levelXpNeeded, int streak, BigDecimal gold, BigDecimal spentToday,
                          BigDecimal spentThisMonth, BudgetGlance budget, List<BudgetRow> budgets,
                          int calories, int calorieGoal, int mealsToday, List<TodayWorkout> workouts,
                          List<EventGlance> events, List<QuestGlance> quests, List<LootGlance> loot,
                          String line, String mood, boolean voiceReady, int voiceRemaining) {
    }

    private static final int LIST_MAX = 3;

    private final CharacterService characters;
    private final LifeService life;
    private final VitalsService vitals;
    private final LedgerQueries ledger;
    private final DeviceVoice voice;
    private final StewardUsage usage;
    private final Clock clock;

    public DeviceGlance(CharacterService characters, LifeService life, VitalsService vitals, LedgerQueries ledger,
                        DeviceVoice voice, StewardUsage usage, Clock clock) {
        this.characters = characters;
        this.life = life;
        this.vitals = vitals;
        this.ledger = ledger;
        this.voice = voice;
        this.usage = usage;
        this.clock = clock;
    }

    public Summary summary(Long userId) {
        CharacterSheet sheet = characters.sheet(userId);
        Today today = life.today(userId);
        LocalDate date = today.date();
        MonthBudget budget = sheet.budget();
        CategoryBudget tightest = budget.categories().isEmpty() ? null : budget.categories().get(0);

        List<EventGlance> events = today.upcoming().stream().limit(LIST_MAX)
                .map(e -> new EventGlance(e.occursOn(), e.atTime() == null ? null : e.atTime().toString(), e.title()))
                .toList();
        List<BudgetRow> budgets = budget.categories().stream().limit(LIST_MAX)
                .map(c -> new BudgetRow(c.category().name(), c.spent(), c.budget(), c.left()))
                .toList();
        List<QuestGlance> quests = sheet.quests().stream().limit(LIST_MAX)
                .map(q -> new QuestGlance(q.name(), q.savedAmount(), q.targetAmount()))
                .toList();
        List<LootGlance> loot = sheet.loot().stream().limit(LIST_MAX).map(DeviceGlance::loot).toList();

        List<Boolean> last7 = sheet.streak().last7();
        GabyLine.Line line = GabyLine.pick(new GabyLine.Day(
                sheet.username(), LocalDateTime.now(clock), today.upcoming(), today.workouts(),
                sheet.streak().current(), !last7.isEmpty() && last7.get(last7.size() - 1),
                tightest == null ? null : tightest.category().name(),
                tightest == null ? null : tightest.left(),
                tightest == null ? null : tightest.budget(),
                today.calories(), today.calorieGoal()));

        return new Summary(date, sheet.username(), sheet.level(), sheet.title(), sheet.xp(), sheet.levelXp(),
                sheet.levelXpNeeded(), sheet.streak().current(), sheet.gold(),
                ledger.total(userId, LedgerEntry.Type.EXPENSE, date, date), sheet.spentThisMonth(),
                tightest == null ? null : new BudgetGlance(budget.left(), budget.totalBudget(), budget.daysLeft(),
                        tightest.category().name(), tightest.left()),
                budgets, today.calories(), today.calorieGoal(), vitals.day(userId, date).meals().size(),
                today.workouts(), events, quests, loot, line.text(), line.mood().name().toLowerCase(Locale.ROOT),
                voice.isReady(), usage.remaining(userId));
    }

    private static LootGlance loot(EntryView e) {
        String label = e.note() != null && !e.note().isBlank() ? e.note()
                : e.category() != null ? e.category().name()
                : e.type() == LedgerEntry.Type.TRANSFER ? "Transfer" : "Entry";
        return new LootGlance(e.occurredOn(), e.type().name().toLowerCase(Locale.ROOT), e.amount(), label);
    }
}
