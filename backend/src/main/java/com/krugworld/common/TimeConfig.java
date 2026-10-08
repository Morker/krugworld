package com.krugworld.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Today", "this month" and "a 7-day streak" all depend on where the user
 * is, not where the server is (the production box runs in UTC). Everything
 * date-related takes this Clock, so tests can also pin the date.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${krugworld.zone:Asia/Kuala_Lumpur}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
