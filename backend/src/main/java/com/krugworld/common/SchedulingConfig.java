package com.krugworld.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Off in tests (krugworld.scheduling.enabled=false) so jobs only run when a test calls them. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "krugworld.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
