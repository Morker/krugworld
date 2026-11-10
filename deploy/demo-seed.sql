-- Demo account for showing KrugWorld off: user "demo", password "demo1234".
--
-- NOT a Flyway migration: it never runs by itself. Run it by hand, locally or
-- on the server, whenever the demo should look fresh:
--   docker compose exec -T db psql -U krugworld -d krugworld < deploy/demo-seed.sql
--
-- Safe to re-run: it deletes the demo account (everything hangs off app_user
-- with ON DELETE CASCADE) and rebuilds it. Every date is relative to today in
-- Kuala Lumpur, so the ledger, streaks and calendar always look current.
-- setseed() makes the "random" spending the same on every run.

BEGIN;
-- Times below ("13:05 lunch") are Malaysian times; the app counts streak days
-- in this zone too.
SET LOCAL timezone = 'Asia/Kuala_Lumpur';

DO $$
DECLARE
    today   date := (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;
    uid     bigint;
    -- accounts
    a_bank  bigint; a_cash bigint; a_tng bigint; a_card bigint;
    -- categories
    c_food bigint; c_transport bigint; c_bills bigint; c_fun bigint; c_shop bigint;
    c_health bigint; c_coffee bigint; c_salary bigint; c_freelance bigint;
    -- recurring rules
    r_salary bigint; r_rent bigint; r_netflix bigint; r_tnb bigint;
    -- workouts
    w_run bigint; w_push bigint; w_legs bigint;
    g_laptop bigint; d_ali bigint; i_done bigint;
    m       int;
    month1  date;
    d       date;
    k       int;
BEGIN
    PERFORM setseed(0.42);

    -- Start clean. admin_audit is the one table that doesn't cascade.
    SELECT id INTO uid FROM app_user WHERE lower(username) = 'demo';
    IF uid IS NOT NULL THEN
        DELETE FROM admin_audit WHERE target_user_id = uid OR admin_id = uid;
        DELETE FROM app_user WHERE id = uid;
    END IF;

    -- BCrypt of "demo1234" (Spring's BCryptPasswordEncoder, cost 10).
    INSERT INTO app_user (username, password_hash, skin, theme, calorie_goal,
                          weather_place, weather_lat, weather_lon, created_at)
    VALUES ('demo', '$2a$10$yOP91bZrVImpAY2stFstOeRay5pEppApZWjoFZM4zCzcg1v7OuNIS',
            'modern', 'steel', 2200, 'Kuala Lumpur, Malaysia', 3.13900, 101.68690,
            now() - interval '100 days')
    RETURNING id INTO uid;

    -- ---- accounts ----
    INSERT INTO account (user_id, name, kind, opening_balance) VALUES (uid, 'Maybank Savings', 'BANK', 4200) RETURNING id INTO a_bank;
    INSERT INTO account (user_id, name, kind, opening_balance) VALUES (uid, 'Purse', 'CASH', 150) RETURNING id INTO a_cash;
    INSERT INTO account (user_id, name, kind, opening_balance) VALUES (uid, 'Touch ''n Go', 'EWALLET', 80) RETURNING id INTO a_tng;
    INSERT INTO account (user_id, name, kind, opening_balance) VALUES (uid, 'Credit card', 'CARD', 0) RETURNING id INTO a_card;

    -- ---- categories (the defaults a new account gets, plus budgets and two extras) ----
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Food', 'EXPENSE', 'food', '#e5606a', 900) RETURNING id INTO c_food;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Transport', 'EXPENSE', 'transport', '#59a8f0', 300) RETURNING id INTO c_transport;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Bills', 'EXPENSE', 'bills', '#e2b453', 1700) RETURNING id INTO c_bills;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Fun', 'EXPENSE', 'fun', '#b48ef0', 250) RETURNING id INTO c_fun;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Shopping', 'EXPENSE', 'shopping', '#f0a35e', 400) RETURNING id INTO c_shop;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Health', 'EXPENSE', 'health', '#62c99a', NULL) RETURNING id INTO c_health;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Coffee', 'EXPENSE', 'coffee', '#a0785a', 120) RETURNING id INTO c_coffee;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Other', 'EXPENSE', 'coin', '#94a1b5', NULL);
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Salary', 'INCOME', 'salary', '#62c99a', NULL) RETURNING id INTO c_salary;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Freelance', 'INCOME', 'coin', '#59a8f0', NULL) RETURNING id INTO c_freelance;
    INSERT INTO category (user_id, name, kind, icon, color, monthly_budget) VALUES (uid, 'Other income', 'INCOME', 'gift', '#e2b453', NULL);

    -- ---- recurring rules ----
    -- next_run_on = the next time the day comes round (today counts if not booked yet).
    INSERT INTO recurring_rule (user_id, type, amount, account_id, category_id, note, day_of_month, next_run_on, auto_post)
    VALUES (uid, 'INCOME', 5200, a_bank, c_salary, 'Salary', 25,
            CASE WHEN extract(day FROM today) < 25 THEN date_trunc('month', today)::date + 24
                 ELSE (date_trunc('month', today) + interval '1 month')::date + 24 END, true)
    RETURNING id INTO r_salary;
    INSERT INTO recurring_rule (user_id, type, amount, account_id, category_id, note, day_of_month, next_run_on, auto_post)
    VALUES (uid, 'EXPENSE', 1300, a_bank, c_bills, 'Rent', 1,
            (date_trunc('month', today) + interval '1 month')::date, true)
    RETURNING id INTO r_rent;
    INSERT INTO recurring_rule (user_id, type, amount, account_id, category_id, note, day_of_month, next_run_on, auto_post)
    VALUES (uid, 'EXPENSE', 55, a_card, c_fun, 'Netflix', 12,
            CASE WHEN extract(day FROM today) < 12 THEN date_trunc('month', today)::date + 11
                 ELSE (date_trunc('month', today) + interval '1 month')::date + 11 END, true)
    RETURNING id INTO r_netflix;
    -- The electricity bill varies, so it waits for the user's seal: due two days
    -- ago and not yet confirmed, so the demo shows "awaiting your seal".
    INSERT INTO recurring_rule (user_id, type, amount, account_id, category_id, note, day_of_month, next_run_on, auto_post)
    VALUES (uid, 'EXPENSE', 180, a_bank, c_bills, 'TNB electricity', extract(day FROM today - 2)::int, today - 2, false)
    RETURNING id INTO r_tnb;

    -- ---- ledger: this month and the three before ----
    FOR m IN 0..3 LOOP
        month1 := (date_trunc('month', today) - make_interval(months => m))::date;

        -- Rule-made entries, only for days that have already passed.
        IF month1 + 24 <= today AND month1 + 24 < (SELECT next_run_on FROM recurring_rule WHERE id = r_salary) THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, recurring_rule_id, created_at)
            VALUES (uid, 'INCOME', 5200, a_bank, c_salary, month1 + 24, 'Salary', r_salary, (month1 + 24) + time '08:00');
        END IF;
        INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, recurring_rule_id, created_at)
        VALUES (uid, 'EXPENSE', 1300, a_bank, c_bills, month1, 'Rent', r_rent, month1 + time '08:00');
        IF month1 + 11 <= today AND month1 + 11 < (SELECT next_run_on FROM recurring_rule WHERE id = r_netflix) THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, recurring_rule_id, created_at)
            VALUES (uid, 'EXPENSE', 55, a_card, c_fun, month1 + 11, 'Netflix', r_netflix, (month1 + 11) + time '08:00');
        END IF;
        -- Past months' electricity, each confirmed with its real amount
        -- (never the one still awaiting its seal).
        d := least(month1 + extract(day FROM today - 2)::int - 1, (month1 + interval '1 month')::date - 1);
        IF d < today - 2 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, recurring_rule_id, created_at)
            VALUES (uid, 'EXPENSE', round((150 + random() * 70)::numeric, 2), a_bank, c_bills, d, 'TNB electricity', r_tnb, d + time '20:15');
        END IF;
        IF m > 0 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', 99, a_bank, c_bills, month1 + 4, 'Unifi fibre', (month1 + 4) + time '21:00');
        END IF;

        -- Monthly top-up from the bank to the e-wallet, and cash from the ATM.
        IF month1 + 2 <= today THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, to_account_id, occurred_on, note, created_at)
            VALUES (uid, 'TRANSFER', 550, a_bank, a_tng, month1 + 2, 'TNG reload', (month1 + 2) + time '09:30');
            INSERT INTO ledger_entry (user_id, type, amount, account_id, to_account_id, occurred_on, note, created_at)
            VALUES (uid, 'TRANSFER', 300, a_bank, a_cash, month1 + 2, 'ATM', (month1 + 2) + time '09:40');
        END IF;
        -- Paying off last month's card.
        IF m > 0 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, to_account_id, occurred_on, note, created_at)
            VALUES (uid, 'TRANSFER', 1000, a_bank, a_card, month1 + 19, 'Card payment', (month1 + 19) + time '22:00');
        END IF;
    END LOOP;

    -- One freelance gig six weeks ago.
    INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
    VALUES (uid, 'INCOME', 850, a_bank, c_freelance, today - 42, 'Landing page for Kak Ros bakery', (today - 42) + time '16:00');

    -- Day-to-day spending for the last ~100 days.
    FOR k IN 0..99 LOOP
        d := today - k;
        -- Lunch almost every day, dinner out some days.
        INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
        VALUES (uid, 'EXPENSE', round((9 + random() * 9)::numeric, 2),
                CASE WHEN random() < 0.5 THEN a_cash ELSE a_tng END, c_food, d,
                (ARRAY['Nasi lemak ayam', 'Chicken rice', 'Mee goreng mamak', 'Nasi campur', 'Roti canai + teh tarik', 'Char kuey teow'])[1 + floor(random() * 6)::int],
                d + time '13:05');
        IF random() < 0.35 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((18 + random() * 40)::numeric, 2), a_card, c_food, d,
                    (ARRAY['Dinner at Nando''s', 'Grab Food', 'Steamboat with friends', 'Sushi King', 'Pasar malam'])[1 + floor(random() * 5)::int],
                    d + time '20:10');
        END IF;
        IF random() < 0.45 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((7 + random() * 8)::numeric, 2), a_tng, c_coffee, d,
                    (ARRAY['ZUS iced latte', 'Kopi O ais', 'Starbucks', 'Tealive'])[1 + floor(random() * 4)::int],
                    d + time '09:20');
        END IF;
        IF extract(isodow FROM d) <= 5 AND random() < 0.6 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((4 + random() * 14)::numeric, 2), a_tng, c_transport, d,
                    (ARRAY['LRT', 'Grab to office', 'Parking', 'Toll'])[1 + floor(random() * 4)::int],
                    d + time '08:10');
        END IF;
        IF k % 9 = 3 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((60 + random() * 30)::numeric, 2), a_card, c_transport, d, 'Petrol RON95', d + time '18:30');
        END IF;
        IF k % 11 = 5 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((40 + random() * 130)::numeric, 2), a_card, c_shop, d,
                    (ARRAY['Shopee haul', 'Uniqlo', 'Mr DIY', 'Lazada', 'Watsons'])[1 + floor(random() * 5)::int],
                    d + time '21:40');
        END IF;
        IF k % 13 = 6 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((20 + random() * 50)::numeric, 2), a_card, c_fun, d,
                    (ARRAY['GSC movie', 'Badminton court', 'Bowling', 'Steam game'])[1 + floor(random() * 4)::int],
                    d + time '19:00');
        END IF;
        IF k % 30 = 17 THEN
            INSERT INTO ledger_entry (user_id, type, amount, account_id, category_id, occurred_on, note, created_at)
            VALUES (uid, 'EXPENSE', round((25 + random() * 40)::numeric, 2), a_cash, c_health, d,
                    (ARRAY['Klinik', 'Guardian pharmacy', 'Vitamins'])[1 + floor(random() * 3)::int],
                    d + time '17:30');
        END IF;
    END LOOP;

    -- XP for logging, as Rewards would have paid it: 5 each, first 10 a day.
    INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
    SELECT uid, 'ENTRY_LOGGED', id::text, 5, created_at
    FROM (SELECT id, created_at, row_number() OVER (PARTITION BY occurred_on ORDER BY id) AS n
          FROM ledger_entry WHERE user_id = uid) e
    WHERE n <= 10;

    -- ---- quests (savings goals) ----
    INSERT INTO goal (user_id, name, icon, target_amount, saved_amount, deadline, created_at)
    VALUES (uid, 'Emergency fund', 'savings', 15000, 6200, today + 240, now() - interval '95 days');
    INSERT INTO goal (user_id, name, icon, target_amount, saved_amount, deadline, created_at)
    VALUES (uid, 'Japan trip with the gang', 'travel', 8000, 2350, today + 150, now() - interval '60 days');
    INSERT INTO goal (user_id, name, icon, target_amount, saved_amount, deadline, created_at)
    VALUES (uid, 'Espresso machine', 'coffee', 1200, 840, today + 45, now() - interval '30 days');
    INSERT INTO goal (user_id, name, icon, target_amount, saved_amount, deadline, completed_at, created_at)
    VALUES (uid, 'New laptop', 'education', 4500, 4500, today - 30, now() - interval '38 days', now() - interval '98 days')
    RETURNING id INTO g_laptop;
    INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
    VALUES (uid, 'QUEST_COMPLETE', g_laptop::text, 200, now() - interval '38 days');

    -- ---- debts ----
    INSERT INTO debt (user_id, direction, person, amount, note, started_on, due_on)
    VALUES (uid, 'THEY_OWE', 'Aiman', 45, 'Steamboat dinner', today - 6, today + 3);
    INSERT INTO debt (user_id, direction, person, amount, note, started_on, due_on)
    VALUES (uid, 'I_OWE', 'Sarah', 240, 'Concert tickets', today - 10, today + 12);
    INSERT INTO debt (user_id, direction, person, amount, note, started_on, due_on)
    VALUES (uid, 'THEY_OWE', 'Hafiz', 300, 'Lent for car repair', today - 40, NULL);
    INSERT INTO debt (user_id, direction, person, amount, note, started_on, due_on, settled_on)
    VALUES (uid, 'I_OWE', 'Ali', 60, 'Futsal booking', today - 25, today - 15, today - 14)
    RETURNING id INTO d_ali;
    INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
    VALUES (uid, 'DEBT_CLEARED', d_ali::text, 10, (today - 14) + time '12:00');

    -- ---- calendar ----
    INSERT INTO calendar_event (user_id, occurs_on, at_time, title) VALUES
        (uid, today,      time '19:30', 'Badminton with Aiman'),
        (uid, today + 1,  time '10:00', 'Sprint planning'),
        (uid, today + 2,  time '15:30', 'Dentist (Dr. Lim)'),
        (uid, today + 4,  NULL,         'Mak''s birthday'),
        (uid, today + 6,  time '12:30', 'Team lunch'),
        (uid, today + 9,  NULL,         'Renew road tax'),
        (uid, today + 13, time '20:00', 'Coldplay concert with Sarah'),
        (uid, today + 20, time '09:00', 'Job interview (backend role)'),
        (uid, today - 3,  time '11:00', 'Car service'),
        (uid, today - 8,  NULL,         'Hari Raya open house'),
        (uid, today - 15, time '14:00', 'Haircut');

    -- ---- workshop ----
    INSERT INTO idea (user_id, title, notes, status, created_at, updated_at) VALUES
        (uid, 'Learn Kubernetes basics', 'Deploy KrugWorld to k3s on the side', 'TODO', now() - interval '20 days', now() - interval '20 days'),
        (uid, 'Write a blog post on Flyway', 'Why migrations never get edited after they run', 'TODO', now() - interval '12 days', now() - interval '12 days'),
        (uid, 'Plan Japan itinerary', 'Tokyo 4 nights, Kyoto 3, Osaka 2', 'DOING', now() - interval '30 days', now() - interval '3 days'),
        (uid, 'Build desk Gaby enclosure', '3D print a stand for the ESP32 screen', 'DOING', now() - interval '18 days', now() - interval '1 day'),
        (uid, 'Sell old monitor on Carousell', '', 'DONE', now() - interval '40 days', now() - interval '33 days'),
        (uid, 'Update CV and LinkedIn', 'Add KrugWorld as a portfolio project', 'DONE', now() - interval '25 days', now() - interval '9 days');
    INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
    SELECT uid, 'IDEA_DONE', id::text, 15, updated_at FROM idea WHERE user_id = uid AND status = 'DONE';

    -- ---- vitals: meals for the last two weeks (and today so far) ----
    FOR k IN 0..13 LOOP
        d := today - k;
        INSERT INTO meal (user_id, eaten_on, name, calories, created_at) VALUES
            (uid, d, (ARRAY['Roti canai', 'Oats with banana', 'Nasi lemak', 'Kaya toast + eggs'])[1 + floor(random() * 4)::int],
             350 + floor(random() * 250)::int, d + time '08:30'),
            (uid, d, (ARRAY['Chicken rice', 'Nasi campur', 'Mee goreng', 'Grilled chicken salad'])[1 + floor(random() * 4)::int],
             550 + floor(random() * 300)::int, d + time '13:10');
        IF k > 0 THEN
            INSERT INTO meal (user_id, eaten_on, name, calories, created_at)
            VALUES (uid, d, (ARRAY['Steamboat', 'Tom yam + rice', 'Salmon and veg', 'Burger'])[1 + floor(random() * 4)::int],
                    600 + floor(random() * 350)::int, d + time '20:00');
        END IF;
        INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
        VALUES (uid, 'MEAL_LOGGED', d::text, 5, d + time '08:30');
    END LOOP;

    -- ---- vitals: training (weekday bits: Mon 1, Tue 2, Wed 4, Thu 8, Fri 16, Sat 32, Sun 64) ----
    INSERT INTO workout (user_id, name, weekdays) VALUES (uid, 'Morning run 5 km', 1 + 4 + 16) RETURNING id INTO w_run;
    INSERT INTO workout (user_id, name, weekdays) VALUES (uid, '50 push-ups', 0) RETURNING id INTO w_push;
    INSERT INTO workout (user_id, name, weekdays) VALUES (uid, 'Gym: legs', 2 + 32) RETURNING id INTO w_legs;
    -- Three weeks of mostly-kept habits; today's are left for the demo to tick.
    FOR k IN 1..21 LOOP
        d := today - k;
        IF random() < 0.85 THEN
            INSERT INTO workout_completion (workout_id, done_on) VALUES (w_push, d);
        END IF;
        IF extract(isodow FROM d) IN (1, 3, 5) AND random() < 0.8 THEN
            INSERT INTO workout_completion (workout_id, done_on) VALUES (w_run, d);
        END IF;
        IF extract(isodow FROM d) IN (2, 6) AND random() < 0.75 THEN
            INSERT INTO workout_completion (workout_id, done_on) VALUES (w_legs, d);
        END IF;
    END LOOP;
    INSERT INTO xp_event (user_id, kind, ref, xp, created_at)
    SELECT uid, 'WORKOUT_DONE', wc.workout_id || ':' || wc.done_on, 10, wc.done_on + time '07:00'
    FROM workout_completion wc JOIN workout w ON w.id = wc.workout_id
    WHERE w.user_id = uid;

    RAISE NOTICE 'Demo account ready: demo / demo1234 (user id %)', uid;
END $$;

COMMIT;
