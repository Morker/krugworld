package com.krugworld.game;

import com.krugworld.auth.UserRepository;
import com.krugworld.budget.BudgetService;
import com.krugworld.budget.BudgetService.MonthBudget;
import com.krugworld.ledger.Category;
import com.krugworld.ledger.CategoryRepository;
import com.krugworld.ledger.LedgerDtos.EntryView;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerQueries;
import com.krugworld.ledger.LedgerService;
import com.krugworld.quest.GoalRepository;
import com.krugworld.quest.QuestService;
import com.krugworld.quest.QuestService.QuestView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the character sheet: the user's finances seen as an RPG hero.
 * Everything here is derived from real data — balances, budgets, goals and
 * the XP log — so the game can never disagree with the ledger.
 */
@Service
public class CharacterService {

    public static final int BUDGET_KEPT_XP = 50;

    public record Streak(int current, int longest, List<Boolean> last7) {
    }

    public record Badge(String id, String name, String description, String icon, boolean earned) {
    }

    public record CharacterSheet(String username, int level, String title, int xp, int levelXp, int levelXpNeeded,
                                 BigDecimal gold, BigDecimal incomeThisMonth, BigDecimal spentThisMonth,
                                 MonthBudget budget, Streak streak, List<Badge> badges,
                                 List<QuestView> quests, List<EntryView> loot) {
    }

    private final UserRepository users;
    private final XpLedger xp;
    private final LedgerQueries ledgerQueries;
    private final LedgerService ledger;
    private final BudgetService budgets;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final QuestService quests;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CharacterService(UserRepository users, XpLedger xp, LedgerQueries ledgerQueries, LedgerService ledger,
                            BudgetService budgets, CategoryRepository categories, GoalRepository goals,
                            QuestService quests, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.xp = xp;
        this.ledgerQueries = ledgerQueries;
        this.ledger = ledger;
        this.budgets = budgets;
        this.categories = categories;
        this.goals = goals;
        this.quests = quests;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CharacterSheet sheet(Long userId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);

        awardKeptBudgets(userId, month);

        int totalXp = xp.total(userId);
        int level = Levels.levelFor(totalXp);
        int levelStart = Levels.threshold(level);
        BigDecimal gold = ledgerQueries.totalBalance(userId);
        Streak streak = streak(userId, today);

        List<QuestView> activeQuests = quests.list(userId).stream()
                .filter(q -> q.completedAt() == null).limit(3).toList();

        return new CharacterSheet(
                users.findById(userId).orElseThrow().getUsername(),
                level, Levels.title(level), totalXp,
                totalXp - levelStart, Levels.threshold(level + 1) - levelStart,
                gold,
                ledgerQueries.total(userId, LedgerEntry.Type.INCOME, month.atDay(1), month.atEndOfMonth()),
                ledgerQueries.total(userId, LedgerEntry.Type.EXPENSE, month.atDay(1), month.atEndOfMonth()),
                budgets.forMonth(userId, month),
                streak,
                badges(userId, level, gold, streak, today),
                activeQuests,
                ledger.recentEntries(userId));
    }

    /**
     * +50 XP for each budget kept in each finished month (up to a year back).
     * Checked whenever the sheet is opened rather than by a month-end job: the
     * unique (user, kind, ref) on xp_event makes re-checking free and safe.
     */
    void awardKeptBudgets(Long userId, YearMonth current) {
        List<Category> budgeted = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .filter(c -> c.getKind() == Category.Kind.EXPENSE && c.getMonthlyBudget() != null && !c.isArchived())
                .toList();
        if (budgeted.isEmpty()) {
            return;
        }
        LocalDate firstEntry = jdbc.queryForObject(
                "SELECT min(occurred_on) FROM ledger_entry WHERE user_id = ?", LocalDate.class, userId);
        if (firstEntry == null) {
            return;
        }
        YearMonth from = YearMonth.from(firstEntry).isAfter(current.minusMonths(12))
                ? YearMonth.from(firstEntry) : current.minusMonths(12);

        for (YearMonth m = from; m.isBefore(current); m = m.plusMonths(1)) {
            Map<Long, BigDecimal> spent = ledgerQueries.totalsByCategory(
                    userId, LedgerEntry.Type.EXPENSE, m.atDay(1), m.atEndOfMonth());
            if (spent.isEmpty()) {
                continue; // nothing logged that month: no reward for an idle month
            }
            for (Category c : budgeted) {
                if (spent.getOrDefault(c.getId(), BigDecimal.ZERO).compareTo(c.getMonthlyBudget()) <= 0) {
                    xp.award(userId, XpLedger.BUDGET_KEPT, m + ":" + c.getId(), BUDGET_KEPT_XP);
                }
            }
        }
    }

    /**
     * Consecutive days with any activity — an entry, a meal, a workout or a
     * finished idea. Today still counts as "in progress".
     */
    Streak streak(Long userId, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(xp.days(userId, XpLedger.ACTIVITY));

        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }

        int longest = 0;
        for (LocalDate d : days) {
            if (!days.contains(d.minusDays(1))) {
                int run = 0;
                while (days.contains(d.plusDays(run))) {
                    run++;
                }
                longest = Math.max(longest, run);
            }
        }

        List<Boolean> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            last7.add(days.contains(today.minusDays(i)));
        }
        return new Streak(current, longest, last7);
    }

    List<Badge> badges(Long userId, int level, BigDecimal gold, Streak streak, LocalDate today) {
        boolean anyEntry = xp.any(userId, XpLedger.ENTRY_LOGGED);
        return List.of(
                new Badge("first_coin", "First coin", "Log your first entry.", "coin", anyEntry),
                new Badge("week_warrior", "Week warrior", "Be active 7 days in a row.", "flame", streak.longest() >= 7),
                new Badge("iron_habit", "Iron habit", "Be active 30 days in a row.", "shield", streak.longest() >= 30),
                new Badge("budget_keeper", "Budget keeper", "Finish a month within a budget.", "scale",
                        xp.any(userId, XpLedger.BUDGET_KEPT)),
                new Badge("quest_complete", "Quest complete", "Reach a savings goal.", "trophy",
                        goals.countByUserIdAndCompletedAtIsNotNull(userId) > 0),
                new Badge("no_spend_sunday", "No-spend Sunday", "Spend nothing on a Sunday you were tracking.", "moon",
                        hadNoSpendSunday(userId, today)),
                new Badge("hoard", "Hoard of a thousand", "Hold RM 1,000 across your accounts.", "coins",
                        gold.compareTo(new BigDecimal("1000")) >= 0),
                new Badge("first_feast", "First feast", "Log a meal.", "bowl", xp.any(userId, XpLedger.MEAL_LOGGED)),
                new Badge("iron_body", "Iron body", "Finish 10 workouts.", "barbell", xp.count(userId, XpLedger.WORKOUT_DONE) >= 10),
                new Badge("idea_smith", "Idea smith", "Finish an idea in the Workshop.", "bulb", xp.any(userId, XpLedger.IDEA_DONE)),
                new Badge("clean_slate", "Clean slate", "Have a debt paid back in full.", "handshake",
                        xp.any(userId, XpLedger.DEBT_CLEARED)),
                new Badge("adept", "Adept", "Reach level 5.", "crown", level >= 5));
    }

    /**
     * A past Sunday with no spending — but only one inside a stretch you were
     * actually tracking (something logged within 3 days either side), so an
     * abandoned account doesn't earn it by doing nothing.
     */
    private boolean hadNoSpendSunday(Long userId, LocalDate today) {
        List<LocalDate> logged = xp.days(userId, List.of(XpLedger.ENTRY_LOGGED));
        if (logged.isEmpty()) {
            return false;
        }
        Set<LocalDate> spendDays = new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT occurred_on FROM ledger_entry WHERE user_id = ? AND type = 'EXPENSE'",
                LocalDate.class, userId));
        Set<LocalDate> loggedSet = new HashSet<>(logged);

        LocalDate sunday = logged.get(0).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (; sunday.isBefore(today); sunday = sunday.plusWeeks(1)) {
            if (spendDays.contains(sunday)) {
                continue;
            }
            for (int d = -3; d <= 3; d++) {
                if (loggedSet.contains(sunday.plusDays(d))) {
                    return true;
                }
            }
        }
        return false;
    }
}
