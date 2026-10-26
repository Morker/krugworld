package com.krugworld.steward;

import com.krugworld.auth.UserRepository;
import com.krugworld.budget.BudgetService;
import com.krugworld.calendar.CalendarService;
import com.krugworld.common.ApiException;
import com.krugworld.debt.Debt;
import com.krugworld.debt.DebtService;
import com.krugworld.game.CharacterService;
import com.krugworld.ledger.LedgerDtos.AccountView;
import com.krugworld.ledger.LedgerDtos.CategoryRequest;
import com.krugworld.ledger.LedgerDtos.CategoryView;
import com.krugworld.ledger.LedgerDtos.EntryRequest;
import com.krugworld.ledger.Category;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerService;
import com.krugworld.quest.QuestService;
import com.krugworld.recurring.RecurringService;
import com.krugworld.vitals.VitalsService;
import com.krugworld.workshop.Idea;
import com.krugworld.workshop.WorkshopService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.krugworld.steward.Schema.bool;
import static com.krugworld.steward.Schema.integer;
import static com.krugworld.steward.Schema.number;
import static com.krugworld.steward.Schema.object;
import static com.krugworld.steward.Schema.props;
import static com.krugworld.steward.Schema.string;
import static com.krugworld.steward.Schema.stringArray;
import static com.krugworld.steward.Schema.stringEnum;

/**
 * Everything the Steward can do, each tool a thin wrapper over the same
 * services the app's own screens use — so its actions follow the same rules
 * (ownership, validation) and earn the same XP. Things are referred to by
 * name where people would (categories, accounts, workouts), by id otherwise.
 */
@Component
public class StewardTools {

    private static final String[] WEEKDAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

    private final LedgerService ledger;
    private final BudgetService budgets;
    private final RecurringService recurring;
    private final DebtService debts;
    private final QuestService quests;
    private final CalendarService calendar;
    private final VitalsService vitals;
    private final WorkshopService workshop;
    private final CharacterService character;
    private final UserRepository users;
    private final Clock clock;
    private final List<StewardTool> all;

    public StewardTools(LedgerService ledger, BudgetService budgets, RecurringService recurring, DebtService debts,
                        QuestService quests, CalendarService calendar, VitalsService vitals, WorkshopService workshop,
                        CharacterService character, UserRepository users, Clock clock) {
        this.ledger = ledger;
        this.budgets = budgets;
        this.recurring = recurring;
        this.debts = debts;
        this.quests = quests;
        this.calendar = calendar;
        this.vitals = vitals;
        this.workshop = workshop;
        this.character = character;
        this.users = users;
        this.clock = clock;
        this.all = List.copyOf(define());
    }

    public List<StewardTool> all() {
        return all;
    }

    /** Runs one tool call; any failure becomes an error result the model can read and explain. */
    public ChatBlock.ToolResult run(Long userId, ChatBlock.ToolUse call) {
        StewardTool tool = all.stream().filter(t -> t.name().equals(call.toolName())).findFirst().orElse(null);
        if (tool == null) {
            return new ChatBlock.ToolResult(call.id(), "No such tool: " + call.toolName(), true);
        }
        try {
            return new ChatBlock.ToolResult(call.id(), tool.handler().execute(userId, call.input()), false);
        } catch (ApiException | StewardException e) {
            return new ChatBlock.ToolResult(call.id(), e.getMessage(), true);
        } catch (Exception e) {
            return new ChatBlock.ToolResult(call.id(), "That didn't work: " + e.getMessage(), true);
        }
    }

    private List<StewardTool> define() {
        List<StewardTool> t = new ArrayList<>();

        // ---------------- overview ----------------

        t.add(new StewardTool("get_overview",
                "The user's character sheet: level, XP, total balance, this month's income and spending, "
                        + "budget left, streak, badges, active quests and recent entries.",
                object(props()),
                (uid, in) -> character.sheet(uid)));

        // ---------------- money ----------------

        t.add(new StewardTool("list_accounts", "Accounts (bank, cash, e-wallet...) with current balances.",
                object(props()),
                (uid, in) -> ledger.listAccounts(uid)));

        t.add(new StewardTool("list_categories", "Spending and income categories, with any monthly budget.",
                object(props()),
                (uid, in) -> ledger.listCategories(uid).stream().filter(c -> !c.archived()).toList()));

        t.add(new StewardTool("list_entries", "Ledger entries for one month, newest first (at most 40).",
                object(props("month", string("YYYY-MM; defaults to this month"))),
                (uid, in) -> {
                    YearMonth month = in.get("month") == null ? YearMonth.now(clock) : YearMonth.parse(str(in, "month"));
                    return ledger.listEntries(uid, month, null, null).stream().limit(40).toList();
                }));

        t.add(new StewardTool("add_entry",
                "Log money in or out, or a transfer between accounts. Category is required for income and "
                        + "expenses (use list_categories if unsure). Account defaults to the first active one.",
                object(props(
                                "type", stringEnum("Kind of entry", "expense", "income", "transfer"),
                                "amount", number("Amount in RM, positive"),
                                "category", string("Category name, for income and expenses"),
                                "account", string("Account name; optional"),
                                "to_account", string("Destination account name, for transfers"),
                                "date", string("YYYY-MM-DD; defaults to today"),
                                "note", string("What it was, e.g. 'nasi lemak'")),
                        "type", "amount"),
                (uid, in) -> {
                    LedgerEntry.Type type = LedgerEntry.Type.valueOf(str(in, "type").toUpperCase());
                    AccountView account = in.get("account") == null ? firstAccount(uid) : account(uid, str(in, "account"));
                    Long toAccount = type == LedgerEntry.Type.TRANSFER ? account(uid, str(in, "to_account")).id() : null;
                    Long category = type == LedgerEntry.Type.TRANSFER ? null
                            : category(uid, str(in, "category"),
                            type == LedgerEntry.Type.INCOME ? Category.Kind.INCOME : Category.Kind.EXPENSE).id();
                    return ledger.createEntry(uid, new EntryRequest(type, money(in, "amount"), account.id(), toAccount,
                            category, date(in, "date"), opt(in, "note")));
                }));

        t.add(new StewardTool("delete_entry", "Delete one ledger entry by id (see list_entries).",
                object(props("id", integer("Entry id")), "id"),
                (uid, in) -> {
                    ledger.deleteEntry(uid, id(in));
                    return "deleted";
                }));

        t.add(new StewardTool("get_budgets", "This month's budgets: how much each budgeted category has left.",
                object(props()),
                (uid, in) -> budgets.forMonth(uid, YearMonth.now(clock))));

        t.add(new StewardTool("set_budget", "Set a spending category's monthly budget; 0 removes it.",
                object(props("category", string("Spending category name"),
                        "monthly_budget", number("RM per month; 0 to remove")), "category", "monthly_budget"),
                (uid, in) -> {
                    CategoryView c = category(uid, str(in, "category"), Category.Kind.EXPENSE);
                    BigDecimal amount = money(in, "monthly_budget");
                    return ledger.updateCategory(uid, c.id(), new CategoryRequest(c.name(), c.kind(), c.icon(), c.color(),
                            amount.signum() > 0 ? amount : null));
                }));

        t.add(new StewardTool("list_recurring", "Recurring entries (salary, rent, subscriptions), when each next runs, "
                        + "and which are awaiting the user's confirmation.",
                object(props()),
                (uid, in) -> recurring.list(uid)));

        t.add(new StewardTool("confirm_recurring",
                "Confirm a recurring entry that is awaiting confirmation (see list_recurring), logging it. "
                        + "Give amount only if this time's amount differs; skip=true lets this one pass unlogged.",
                object(props("id", integer("Recurring entry id"), "amount", number("RM; optional"),
                        "skip", bool("True to skip this occurrence instead")), "id"),
                (uid, in) -> {
                    RecurringService.RuleView rule = recurring.list(uid).stream()
                            .filter(r -> r.id().equals(id(in)) && r.awaitingConfirmation()).findFirst()
                            .orElseThrow(() -> new StewardException("That recurring entry isn't awaiting confirmation."));
                    if (Boolean.TRUE.equals(in.get("skip"))) {
                        return recurring.skip(uid, rule.id(), new RecurringService.SkipRequest(rule.nextRunOn()));
                    }
                    return recurring.confirm(uid, rule.id(), new RecurringService.ConfirmRequest(rule.nextRunOn(),
                            in.get("amount") == null ? null : money(in, "amount")));
                }));

        t.add(new StewardTool("add_recurring", "Add something that repeats monthly, logged automatically on its day.",
                object(props(
                                "type", stringEnum("Money in or out", "expense", "income"),
                                "amount", number("RM"),
                                "category", string("Category name"),
                                "day_of_month", integer("1-31; 29-31 fall on the last day of shorter months"),
                                "account", string("Account name; optional"),
                                "note", string("What it is, e.g. 'Rent'"),
                                "auto", bool("True (default): logged by itself on the day. False: waits for the user "
                                        + "to confirm it each month (for bills whose amount varies)")),
                        "type", "amount", "category", "day_of_month"),
                (uid, in) -> {
                    LedgerEntry.Type type = LedgerEntry.Type.valueOf(str(in, "type").toUpperCase());
                    AccountView account = in.get("account") == null ? firstAccount(uid) : account(uid, str(in, "account"));
                    Long category = category(uid, str(in, "category"),
                            type == LedgerEntry.Type.INCOME ? Category.Kind.INCOME : Category.Kind.EXPENSE).id();
                    return recurring.create(uid, new RecurringService.RuleRequest(type, money(in, "amount"), account.id(),
                            category, intVal(in, "day_of_month"), null, opt(in, "note"), true,
                            !Boolean.FALSE.equals(in.get("auto"))));
                }));

        t.add(new StewardTool("list_quests", "Savings quests (goals) and their progress.",
                object(props()),
                (uid, in) -> quests.list(uid)));

        t.add(new StewardTool("add_quest", "Start a savings quest.",
                object(props("name", string("What it's for"), "target_amount", number("RM"),
                        "deadline", string("YYYY-MM-DD; optional")), "name", "target_amount"),
                (uid, in) -> quests.create(uid, new QuestService.QuestRequest(str(in, "name"), "savings",
                        money(in, "target_amount"), in.get("deadline") == null ? null : LocalDate.parse(str(in, "deadline"))))));

        t.add(new StewardTool("contribute_to_quest", "Put money towards a quest (negative to take some back).",
                object(props("quest", string("Quest name"), "amount", number("RM")), "quest", "amount"),
                (uid, in) -> {
                    String name = str(in, "quest");
                    QuestService.QuestView q = quests.list(uid).stream()
                            .filter(v -> v.name().equalsIgnoreCase(name)).findFirst()
                            .orElseThrow(() -> new StewardException("No quest called '" + name + "'."));
                    return quests.contribute(uid, q.id(), new QuestService.ContributionRequest(money(in, "amount")));
                }));

        // ---------------- debts ----------------

        t.add(new StewardTool("list_debts",
                "Debts between the user and other people: who owes the user (THEY_OWE) and whom the user owes "
                        + "(I_OWE), how much is still outstanding, due dates and repayments. Open ones first.",
                object(props()),
                (uid, in) -> debts.list(uid)));

        t.add(new StewardTool("add_debt", "Remember a new debt: someone borrowed from the user, or the user borrowed.",
                object(props(
                                "direction", stringEnum("they_owe: the person owes the user; i_owe: the user owes them",
                                        "they_owe", "i_owe"),
                                "person", string("Who, e.g. 'Ali'"),
                                "amount", number("RM"),
                                "note", string("What for; optional"),
                                "due_on", string("YYYY-MM-DD; optional")),
                        "direction", "person", "amount"),
                (uid, in) -> debts.create(uid, new DebtService.DebtRequest(
                        Debt.Direction.valueOf(str(in, "direction").toUpperCase()), str(in, "person"),
                        money(in, "amount"), opt(in, "note"), null,
                        in.get("due_on") == null ? null : LocalDate.parse(str(in, "due_on"))))));

        t.add(new StewardTool("record_debt_payment",
                "Record money paid back on an open debt with that person (the oldest one if there are several). "
                        + "Paying the rest settles it.",
                object(props("person", string("Who"), "amount", number("RM paid back"),
                        "direction", stringEnum("Which way, if the user both owes and is owed by this person",
                                "they_owe", "i_owe")),
                        "person", "amount"),
                (uid, in) -> {
                    String person = str(in, "person");
                    String direction = in.get("direction") == null ? null : str(in, "direction").toUpperCase();
                    DebtService.DebtView debt = debts.list(uid).stream()
                            .filter(d -> d.settledOn() == null && d.person().equalsIgnoreCase(person)
                                    && (direction == null || d.direction().name().equals(direction)))
                            .min(Comparator.comparing(DebtService.DebtView::startedOn))
                            .orElseThrow(() -> new StewardException("No open debt with '" + person + "'."));
                    return debts.pay(uid, debt.id(), new DebtService.PaymentRequest(money(in, "amount"), null, ""));
                }));

        // ---------------- calendar ----------------

        t.add(new StewardTool("list_events", "Calendar events between two dates (defaults: today to 30 days ahead).",
                object(props("from", string("YYYY-MM-DD"), "to", string("YYYY-MM-DD"))),
                (uid, in) -> {
                    LocalDate from = date(in, "from");
                    LocalDate to = in.get("to") == null ? from.plusDays(30) : LocalDate.parse(str(in, "to"));
                    return calendar.between(uid, from, to);
                }));

        t.add(new StewardTool("add_event", "Put something on the calendar.",
                object(props("title", string("What"), "date", string("YYYY-MM-DD; defaults to today"),
                        "time", string("HH:MM, 24-hour; omit for all day")), "title"),
                (uid, in) -> calendar.create(uid, new CalendarService.EventRequest(date(in, "date"),
                        in.get("time") == null ? null : LocalTime.parse(str(in, "time")), str(in, "title")))));

        t.add(new StewardTool("delete_event", "Remove a calendar event by id (see list_events).",
                object(props("id", integer("Event id")), "id"),
                (uid, in) -> {
                    calendar.delete(uid, id(in));
                    return "deleted";
                }));

        // ---------------- meals ----------------

        t.add(new StewardTool("get_meals", "One day's meals and calories against the daily goal.",
                object(props("date", string("YYYY-MM-DD; defaults to today"))),
                (uid, in) -> vitals.day(uid, date(in, "date"))));

        t.add(new StewardTool("add_meal", "Log a meal. Estimate calories sensibly if the user doesn't say.",
                object(props("name", string("What was eaten"), "calories", integer("kcal"),
                        "date", string("YYYY-MM-DD; defaults to today")), "name", "calories"),
                (uid, in) -> vitals.addMeal(uid, new VitalsService.MealRequest(str(in, "name"),
                        intVal(in, "calories"), date(in, "date")))));

        t.add(new StewardTool("set_calorie_goal", "Change the daily calorie goal (500-10000).",
                object(props("calories", integer("kcal per day")), "calories"),
                (uid, in) -> {
                    int goal = intVal(in, "calories");
                    if (goal < 500 || goal > 10000) {
                        throw new StewardException("A goal must be between 500 and 10000 kcal.");
                    }
                    var user = users.findById(uid).orElseThrow();
                    user.setCalorieGoal(goal);
                    users.save(user);
                    return Map.of("calorieGoal", goal);
                }));

        // ---------------- training ----------------

        t.add(new StewardTool("list_workouts", "Training plan: each workout, its days, whether done today, and its streak.",
                object(props("date", string("YYYY-MM-DD; defaults to today"))),
                (uid, in) -> vitals.workouts(uid, date(in, "date"))));

        t.add(new StewardTool("add_workout", "Add a workout to the training plan.",
                object(props("name", string("e.g. 'Morning run'"),
                        "days", stringArray("Weekdays it's planned for; empty means any day", WEEKDAYS)), "name"),
                (uid, in) -> vitals.createWorkout(uid, new VitalsService.WorkoutRequest(str(in, "name"), days(in)),
                        LocalDate.now(clock))));

        t.add(new StewardTool("mark_workout_done", "Tick a workout off for a day (or untick it).",
                object(props("workout", string("Workout name"), "date", string("YYYY-MM-DD; defaults to today"),
                        "done", bool("false to untick; default true")), "workout"),
                (uid, in) -> {
                    String name = str(in, "workout");
                    var w = vitals.workouts(uid, LocalDate.now(clock)).stream()
                            .filter(v -> v.name().equalsIgnoreCase(name)).findFirst()
                            .orElseThrow(() -> new StewardException("No workout called '" + name + "'."));
                    return vitals.setDone(uid, w.id(), date(in, "date"), !Boolean.FALSE.equals(in.get("done")));
                }));

        // ---------------- workshop ----------------

        t.add(new StewardTool("list_ideas", "The workshop board: ideas to do, in progress, and done.",
                object(props()),
                (uid, in) -> workshop.list(uid)));

        t.add(new StewardTool("add_idea", "Pin a new idea to the workshop board.",
                object(props("title", string("The idea"), "notes", string("Optional detail")), "title"),
                (uid, in) -> workshop.create(uid, new WorkshopService.IdeaRequest(str(in, "title"), opt(in, "notes"), null))));

        t.add(new StewardTool("set_idea_status", "Move an idea along: TODO, DOING or DONE (see list_ideas for ids).",
                object(props("id", integer("Idea id"), "status", stringEnum("New status", "TODO", "DOING", "DONE")),
                        "id", "status"),
                (uid, in) -> {
                    var idea = workshop.list(uid).stream().filter(i -> i.id().equals(id(in))).findFirst()
                            .orElseThrow(() -> ApiException.notFound("Idea"));
                    return workshop.update(uid, idea.id(), new WorkshopService.IdeaRequest(idea.title(), idea.notes(),
                            Idea.Status.valueOf(str(in, "status").toUpperCase())));
                }));

        return t;
    }

    // ---------------- lookups by name ----------------

    private AccountView firstAccount(Long uid) {
        return ledger.listAccounts(uid).stream().filter(a -> !a.archived()).findFirst()
                .orElseThrow(() -> new StewardException("There are no accounts yet."));
    }

    private AccountView account(Long uid, String name) {
        List<AccountView> active = ledger.listAccounts(uid).stream().filter(a -> !a.archived()).toList();
        return active.stream().filter(a -> a.name().equalsIgnoreCase(name.trim())).findFirst()
                .orElseThrow(() -> new StewardException("No account called '" + name + "'. Accounts: "
                        + active.stream().map(AccountView::name).toList()));
    }

    private CategoryView category(Long uid, String name, Category.Kind kind) {
        List<CategoryView> choices = ledger.listCategories(uid).stream()
                .filter(c -> c.kind() == kind && !c.archived()).toList();
        return choices.stream().filter(c -> c.name().equalsIgnoreCase(name.trim())).findFirst()
                .orElseThrow(() -> new StewardException("No " + kind.name().toLowerCase() + " category called '" + name
                        + "'. Choose one of: " + choices.stream().map(CategoryView::name).toList()));
    }

    // ---------------- input readers ----------------

    private static String str(Map<String, Object> in, String key) {
        Object v = in.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new StewardException("Missing '" + key + "'.");
        }
        return v.toString().trim();
    }

    private static String opt(Map<String, Object> in, String key) {
        Object v = in.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static BigDecimal money(Map<String, Object> in, String key) {
        try {
            return new BigDecimal(str(in, key)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new StewardException("'" + key + "' must be a number.");
        }
    }

    private static int intVal(Map<String, Object> in, String key) {
        try {
            return (int) Math.round(Double.parseDouble(str(in, key)));
        } catch (NumberFormatException e) {
            throw new StewardException("'" + key + "' must be a whole number.");
        }
    }

    private static Long id(Map<String, Object> in) {
        return (long) intVal(in, "id");
    }

    private LocalDate date(Map<String, Object> in, String key) {
        return in.get(key) == null ? LocalDate.now(clock) : LocalDate.parse(str(in, key));
    }

    private static Set<DayOfWeek> days(Map<String, Object> in) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (in.get("days") instanceof List<?> list) {
            for (Object d : list) {
                days.add(DayOfWeek.valueOf(d.toString().trim().toUpperCase()));
            }
        }
        return days;
    }
}
