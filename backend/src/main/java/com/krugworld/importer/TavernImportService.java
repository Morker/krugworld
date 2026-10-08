package com.krugworld.importer;

import com.krugworld.common.ApiException;
import com.krugworld.ledger.Account;
import com.krugworld.ledger.AccountRepository;
import com.krugworld.ledger.Category;
import com.krugworld.ledger.CategoryRepository;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerEntryRepository;
import com.krugworld.recurring.RecurringRule;
import com.krugworld.recurring.RecurringRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.krugworld.auth.UserRepository;
import com.krugworld.calendar.CalendarEvent;
import com.krugworld.calendar.CalendarEventRepository;
import com.krugworld.vitals.Meal;
import com.krugworld.vitals.MealRepository;
import com.krugworld.vitals.Workout;
import com.krugworld.vitals.WorkoutRepository;
import com.krugworld.workshop.Idea;
import com.krugworld.workshop.IdeaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off import of everything from Krugger's Tavern, which kept each
 * feature as a JSON blob: {@code kt-finance}, {@code kt-commitments},
 * {@code kt-calendar}, {@code kt-calorie-log}, {@code kt-calorie-goal},
 * {@code kt-workouts} and {@code kt-ideas}.
 *
 * Accepts any of: Tavern's own GET /api/kv response, an object keyed by
 * those names, or a bare array of finance entries. Entries land in a new
 * "Tavern purse" account (which also marks the import as done);
 * commitments become recurring expenses. No XP is given for imported
 * history — it wasn't done here.
 */
@Service
public class TavernImportService {

    public static final String IMPORT_ACCOUNT = "Tavern purse";

    public record ImportResult(int entries, int recurring, int categoriesCreated, int events, int meals,
                               int workouts, int ideas, int skipped) {
    }

    private static final Map<String, DayOfWeek> TAVERN_DAYS = Map.of(
            "Mon", DayOfWeek.MONDAY, "Tue", DayOfWeek.TUESDAY, "Wed", DayOfWeek.WEDNESDAY,
            "Thu", DayOfWeek.THURSDAY, "Fri", DayOfWeek.FRIDAY, "Sat", DayOfWeek.SATURDAY, "Sun", DayOfWeek.SUNDAY);

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final LedgerEntryRepository entries;
    private final RecurringRuleRepository rules;
    private final CalendarEventRepository events;
    private final MealRepository meals;
    private final WorkoutRepository workouts;
    private final IdeaRepository ideas;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public TavernImportService(AccountRepository accounts, CategoryRepository categories,
                               LedgerEntryRepository entries, RecurringRuleRepository rules,
                               CalendarEventRepository events, MealRepository meals, WorkoutRepository workouts,
                               IdeaRepository ideas, UserRepository users, JdbcTemplate jdbc,
                               ObjectMapper json, Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.entries = entries;
        this.rules = rules;
        this.events = events;
        this.meals = meals;
        this.workouts = workouts;
        this.ideas = ideas;
        this.users = users;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importTavern(Long userId, JsonNode payload) {
        JsonNode finance = section(payload, "kt-finance");
        JsonNode commitments = section(payload, "kt-commitments");
        JsonNode calendar = section(payload, "kt-calendar");
        JsonNode calorieLog = section(payload, "kt-calorie-log");
        JsonNode calorieGoal = section(payload, "kt-calorie-goal");
        JsonNode training = section(payload, "kt-workouts");
        JsonNode ideaBoard = section(payload, "kt-ideas");
        if (finance.isEmpty() && commitments.isEmpty() && calendar.isEmpty() && calorieLog.isEmpty()
                && training.isEmpty() && ideaBoard.isEmpty() && !calorieGoal.isNumber()) {
            throw ApiException.badRequest("nothing_to_import", "No Krugger's Tavern data found in that file.");
        }
        boolean alreadyImported = accounts.findByUserIdOrderByArchivedAscIdAsc(userId).stream()
                .anyMatch(a -> a.getName().equals(IMPORT_ACCOUNT));
        if (alreadyImported) {
            throw new ApiException(HttpStatus.CONFLICT, "already_imported",
                    "Your Tavern ledger is already here (see the \"" + IMPORT_ACCOUNT + "\" account).");
        }

        Account purse = accounts.save(new Account(userId, IMPORT_ACCOUNT, Account.Kind.CASH, BigDecimal.ZERO));
        CategoryCache cats = new CategoryCache(userId);
        int imported = 0;
        int skipped = 0;

        for (JsonNode e : finance) {
            BigDecimal amount = amount(e.path("amount"));
            LocalDate date = date(e.path("date").asText(""));
            if (amount == null || date == null) {
                skipped++;
                continue;
            }
            boolean income = "income".equalsIgnoreCase(e.path("type").asText());
            LedgerEntry entry = new LedgerEntry(userId);
            entry.setType(income ? LedgerEntry.Type.INCOME : LedgerEntry.Type.EXPENSE);
            entry.setAmount(amount);
            entry.setAccountId(purse.getId());
            entry.setCategoryId(cats.get(e.path("category").asText(""),
                    income ? Category.Kind.INCOME : Category.Kind.EXPENSE).getId());
            entry.setOccurredOn(date);
            entry.setNote(truncate(e.path("name").asText(""), 200));
            entries.save(entry);
            imported++;
        }

        int recurring = 0;
        LocalDate today = LocalDate.now(clock);
        for (JsonNode c : commitments) {
            BigDecimal amount = amount(c.path("amount"));
            int day = c.path("dueDay").asInt(0);
            if (amount == null || day < 1) {
                skipped++;
                continue;
            }
            RecurringRule rule = new RecurringRule(userId);
            rule.setType(LedgerEntry.Type.EXPENSE);
            rule.setAmount(amount);
            rule.setAccountId(purse.getId());
            rule.setCategoryId(cats.get(c.path("category").asText(""), Category.Kind.EXPENSE).getId());
            rule.setDayOfMonth(Math.min(day, 31));
            rule.setNote(truncate(c.path("name").asText(""), 200));
            LocalDate next = rule.firstRunOnOrAfter(today);
            // Already ticked off in Tavern for that month? Start the month after.
            if (paid(c.path("paidMonths"), YearMonth.from(next))) {
                next = rule.dateIn(YearMonth.from(next).plusMonths(1));
            }
            rule.setNextRunOn(next);
            rules.save(rule);
            recurring++;
        }

        Counts life = importLife(userId, calendar, calorieLog, calorieGoal, training, ideaBoard);

        return new ImportResult(imported, recurring, cats.created, life.events, life.meals, life.workouts,
                life.ideas, skipped + life.skipped);
    }

    private static final class Counts {
        int events;
        int meals;
        int workouts;
        int ideas;
        int skipped;
    }

    /** Calendar, meals, calorie goal, training and the idea board, saved straight to their tables: no XP. */
    private Counts importLife(Long userId, JsonNode calendar, JsonNode calorieLog, JsonNode calorieGoal,
                              JsonNode training, JsonNode ideaBoard) {
        Counts n = new Counts();

        // kt-calendar: {"2026-10-09": [{"text": "..."}], ...}
        for (Iterator<Map.Entry<String, JsonNode>> it = calendar.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> day = it.next();
            LocalDate date = date(day.getKey());
            for (JsonNode ev : day.getValue()) {
                String title = truncate(ev.path("text").asText(""), 200);
                if (date == null || title.isEmpty()) {
                    n.skipped++;
                    continue;
                }
                CalendarEvent event = new CalendarEvent(userId);
                event.setOccursOn(date);
                event.setTitle(title);
                events.save(event);
                n.events++;
            }
        }

        // kt-calorie-log: {"2026-10-08": [{"name": "...", "cal": 650}], ...}
        for (Iterator<Map.Entry<String, JsonNode>> it = calorieLog.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> day = it.next();
            LocalDate date = date(day.getKey());
            for (JsonNode food : day.getValue()) {
                int cal = food.path("cal").asInt(-1);
                if (date == null || cal < 0) {
                    n.skipped++;
                    continue;
                }
                String name = truncate(food.path("name").asText(""), 100);
                meals.save(new Meal(userId, date, name.isEmpty() ? "Meal" : name, Math.min(cal, 20000)));
                n.meals++;
            }
        }

        if (calorieGoal.isNumber() && calorieGoal.asInt() >= 500 && calorieGoal.asInt() <= 10000) {
            users.findById(userId).ifPresent(u -> u.setCalorieGoal(calorieGoal.asInt()));
        }

        // kt-workouts: [{"name": "...", "days": ["Mon", "Wed"], "completedDates": ["2026-10-06"]}]
        for (JsonNode w : training) {
            String name = truncate(w.path("name").asText(""), 80);
            if (name.isEmpty()) {
                n.skipped++;
                continue;
            }
            Workout workout = new Workout(userId);
            workout.setName(name);
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (JsonNode d : w.path("days")) {
                DayOfWeek day = TAVERN_DAYS.get(d.asText());
                if (day != null) {
                    days.add(day);
                }
            }
            workout.setDays(days);
            Workout saved = workouts.save(workout);
            for (JsonNode done : w.path("completedDates")) {
                LocalDate date = date(done.asText());
                if (date != null) {
                    jdbc.update("INSERT INTO workout_completion (workout_id, done_on) VALUES (?, ?) ON CONFLICT DO NOTHING",
                            saved.getId(), date);
                }
            }
            n.workouts++;
        }

        // kt-ideas: [{"title": "...", "notes": "...", "status": "todo" | "progress" | "done"}]
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (JsonNode i : ideaBoard) {
            String title = truncate(i.path("title").asText(""), 120);
            if (title.isEmpty()) {
                n.skipped++;
                continue;
            }
            Idea idea = new Idea(userId);
            idea.setTitle(title);
            idea.setNotes(truncate(i.path("notes").asText(""), 1000));
            idea.setStatus(switch (i.path("status").asText()) {
                case "progress" -> Idea.Status.DOING;
                case "done" -> Idea.Status.DONE;
                default -> Idea.Status.TODO;
            });
            idea.setUpdatedAt(now);
            ideas.save(idea);
            n.ideas++;
        }
        return n;
    }

    /** Finds {@code key} in whichever of the accepted shapes the payload is; an empty node if absent. */
    private JsonNode section(JsonNode payload, String key) {
        if (payload.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            payload.forEach(list::add);
            boolean kvExport = !list.isEmpty() && list.get(0).has("key") && list.get(0).has("value");
            if (!kvExport) {
                return key.equals("kt-finance") ? payload : MissingNode.getInstance();
            }
            for (JsonNode row : list) {
                if (key.equals(row.path("key").asText())) {
                    return parseMaybeString(row.get("value"));
                }
            }
            return MissingNode.getInstance();
        }
        return payload.has(key) ? parseMaybeString(payload.get(key)) : MissingNode.getInstance();
    }

    /** Tavern stored values as JSON text inside JSON, so a value may need parsing twice. */
    private JsonNode parseMaybeString(JsonNode value) {
        try {
            JsonNode node = value.isTextual() ? json.readTree(value.asText()) : value;
            return node == null ? MissingNode.getInstance() : node;
        } catch (Exception e) {
            throw ApiException.badRequest("unreadable", "That Tavern data couldn't be read.");
        }
    }

    private static BigDecimal amount(JsonNode node) {
        if (!node.isNumber() && !node.isTextual()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(node.asText()).setScale(2, RoundingMode.HALF_UP);
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean paid(JsonNode paidMonths, YearMonth month) {
        for (JsonNode m : paidMonths) {
            if (month.toString().equals(m.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Matches Tavern's free-text categories to existing ones by name, creating any that are missing. */
    private final class CategoryCache {
        private final Long userId;
        private final Map<String, Category> seen = new HashMap<>();
        int created;

        CategoryCache(Long userId) {
            this.userId = userId;
        }

        Category get(String rawName, Category.Kind kind) {
            String name = truncate(rawName, 40);
            if (name.isEmpty()) {
                name = kind == Category.Kind.INCOME ? "Other income" : "Other";
            }
            String key = kind + ":" + name.toLowerCase();
            String finalName = name;
            return seen.computeIfAbsent(key, k -> categories
                    .findByUserIdAndKindAndNameIgnoreCase(userId, kind, finalName)
                    .orElseGet(() -> {
                        created++;
                        return categories.save(new Category(userId, finalName, kind, "coin", "#94a1b5", null));
                    }));
        }
    }
}
