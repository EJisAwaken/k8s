package org.esoalahy.stock.core.audit.dto;

import org.esoalahy.stock.core.audit.AuditLog;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        String entityName,
        String entityId,
        String action,
        /* JSON : instantané (INSERT/DELETE) ou {champ: {old, new}} (UPDATE). */
        String changes,
        String performedBy,
        Instant performedAt
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getEntityName(), a.getEntityId(), a.getAction(),
                a.getChanges(), a.getPerformedBy(), a.getPerformedAt());
    }
}
