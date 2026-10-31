package org.esoalahy.stock.core.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persiste les entrées d'audit dans une transaction indépendante.
 *
 * <p>{@code REQUIRES_NEW} découple l'écriture du journal de la transaction
 * métier : l'audit ne peut ni rollback ni faire échouer l'opération auditée, et
 * l'écriture peut avoir lieu après le commit métier (via une synchronisation).
 */
@Service
@RequiredArgsConstructor
public class AuditWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAll(List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        auditLogRepository.saveAll(logs);
    }
}
