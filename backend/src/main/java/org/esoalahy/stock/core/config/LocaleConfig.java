package org.esoalahy.stock.core.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Fuseau horaire par défaut de l'application = Indian/Antananarivo.
 *
 * <p>La base reste en timestamptz (UTC) ; ce réglage n'affecte que les
 * conversions {@code Instant → LocalDate/heure locale} faites au rendu (PDF,
 * libellés) via {@code ZoneId.systemDefault()}, pour que « aujourd'hui » et les
 * horodatages imprimés (bons, étiquettes) correspondent à l'heure de
 * Madagascar (UTC+3).</p>
 *
 * <p>⚠️ Leçon du socle : PAS de {@code hibernate.jdbc.time_zone=UTC} — avec une
 * JVM en UTC+3, il décalait les colonnes {@code time without time zone}
 * (heures « murales ») de −3h au stockage.</p>
 */
@Slf4j
@Configuration
public class LocaleConfig {

    @Value("${app.timezone:Indian/Antananarivo}")
    private String timezone;

    @PostConstruct
    public void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        log.info("Fuseau horaire par défaut : {}", timezone);
    }
}
