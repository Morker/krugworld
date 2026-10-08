package com.coffold.importer;

import com.coffold.common.ApiException;
import com.coffold.ledger.Account;
import com.coffold.ledger.AccountRepository;
import com.coffold.ledger.Category;
import com.coffold.ledger.CategoryRepository;
import com.coffold.ledger.LedgerEntry;
import com.coffold.ledger.LedgerEntryRepository;
import com.coffold.recurring.RecurringRule;
import com.coffold.recurring.RecurringRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off import of a Krugger's Tavern ledger. Tavern 1 kept the ledger as
 * JSON blobs: {@code kt-finance} ({@code {name, amount, type, category, date}})
 * and {@code kt-commitments} ({@code {name, amount, dueDay, category, paidMonths}}).
 *
 * Accepts any of: Tavern's own GET /api/kv response, an object keyed by
 * those names, or a bare array of finance entries. Entries land in a new
 * "Tavern purse" account; commitments become recurring expenses. No XP is
 * given for imported history — it wasn't logged here.
 */
@Service
public class TavernImportService {

    public static final String IMPORT_ACCOUNT = "Tavern purse";

    public record ImportResult(int entries, int recurring, int categoriesCreated, int skipped) {
    }

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final LedgerEntryRepository entries;
    private final RecurringRuleRepository rules;
    private final ObjectMapper json;
    private final Clock clock;

    public TavernImportService(AccountRepository accounts, CategoryRepository categories,
                               LedgerEntryRepository entries, RecurringRuleRepository rules,
                               ObjectMapper json, Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.entries = entries;
        this.rules = rules;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importTavern(Long userId, JsonNode payload) {
        JsonNode finance = section(payload, "kt-finance");
        JsonNode commitments = section(payload, "kt-commitments");
        if (finance.isEmpty() && commitments.isEmpty()) {
            throw ApiException.badRequest("nothing_to_import", "No Tavern ledger found in that file.");
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

        return new ImportResult(imported, recurring, cats.created, skipped);
    }

    /** Finds {@code key} in whichever of the accepted shapes the payload is. */
    private JsonNode section(JsonNode payload, String key) {
        if (payload.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            payload.forEach(list::add);
            boolean kvExport = !list.isEmpty() && list.get(0).has("key") && list.get(0).has("value");
            if (!kvExport) {
                return key.equals("kt-finance") ? payload : json.createArrayNode();
            }
            for (JsonNode row : list) {
                if (key.equals(row.path("key").asText())) {
                    return parseMaybeString(row.get("value"));
                }
            }
            return json.createArrayNode();
        }
        return payload.has(key) ? parseMaybeString(payload.get(key)) : json.createArrayNode();
    }

    /** Tavern stored values as JSON text inside JSON, so a value may need parsing twice. */
    private JsonNode parseMaybeString(JsonNode value) {
        try {
            JsonNode node = value.isTextual() ? json.readTree(value.asText()) : value;
            return node != null && node.isArray() ? node : json.createArrayNode();
        } catch (Exception e) {
            throw ApiException.badRequest("unreadable", "That Tavern ledger couldn't be read.");
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
