package org.esoalahy.stock.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Purge planifiée de la blacklist JWT ; journal de connexion écrit en asynchrone. */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
