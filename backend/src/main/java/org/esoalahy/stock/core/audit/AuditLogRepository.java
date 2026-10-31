package org.esoalahy.stock.core.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityName IS NULL OR a.entityName = :entityName)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:action     IS NULL OR a.action = :action)
              AND (:performedBy IS NULL OR LOWER(a.performedBy) LIKE LOWER(CONCAT('%', CAST(:performedBy AS string), '%')))
            ORDER BY a.performedAt DESC, a.id DESC
            """)
    Page<AuditLog> search(@Param("entityName") String entityName,
                          @Param("entityId") String entityId,
                          @Param("action") String action,
                          @Param("performedBy") String performedBy,
                          Pageable pageable);

    @Query("SELECT DISTINCT a.entityName FROM AuditLog a ORDER BY a.entityName")
    List<String> entityNames();
}
