package org.esoalahy.stock.core.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entrée du journal d'audit générique — une action CRUD sur une entité
 * métier, avec l'auteur, l'horodatage et le détail des changements en JSON.
 * L'anti-coulage commence ici : toute opération d'argent ou d'annulation laisse
 * une trace consultable.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "changes", columnDefinition = "text")
    private String changes;

    @Column(name = "performed_by", length = 255)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;
}
