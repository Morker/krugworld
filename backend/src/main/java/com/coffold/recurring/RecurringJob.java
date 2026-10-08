package com.coffold.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Books due recurring entries shortly after midnight (in the app's time zone),
 * and once at startup to catch up on anything missed while the app was down.
 */
@Component
public class RecurringJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringJob.class);

    private final RecurringService recurring;
    private final Clock clock;

    public RecurringJob(RecurringService recurring, Clock clock) {
        this.recurring = recurring;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${coffold.zone:Asia/Kuala_Lumpur}")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    private void run() {
        int booked = recurring.runDue(LocalDate.now(clock));
        if (booked > 0) {
            log.info("Booked {} recurring entr{}", booked, booked == 1 ? "y" : "ies");
        }
    }
}
