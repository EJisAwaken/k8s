package org.esoalahy.stock.core.config;

import org.esoalahy.stock.core.audit.AuditEventListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

/**
 * enregistre {@link AuditEventListener} dans le registre d'événements
 * Hibernate au démarrage, pour capturer toutes les écritures d'entités —
 * l'audit anti-coulage couvre ainsi chaque module métier par construction.
 */
@Component
@RequiredArgsConstructor
public class AuditListenerRegistrar {

    private final EntityManagerFactory entityManagerFactory;
    private final AuditEventListener auditEventListener;

    @PostConstruct
    public void register() {
        SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry =
                sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);

        registry.appendListeners(EventType.POST_INSERT, auditEventListener);
        registry.appendListeners(EventType.POST_UPDATE, auditEventListener);
        registry.appendListeners(EventType.POST_DELETE, auditEventListener);
    }
}
