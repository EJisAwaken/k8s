package org.esoalahy.stock.core.audit;

import org.esoalahy.stock.core.audit.dto.AuditLogDto;
import org.esoalahy.stock.core.pagination.PageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Consultation du journal d'audit : administrateur et comptable (lecture). */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','COMPTABLE')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<AuditLogDto> search(@RequestParam(required = false) String entityName,
                                       @RequestParam(required = false) String entityId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String performedBy,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return PageDto.of(auditLogRepository.search(
                blank(entityName), blank(entityId), blank(action), blank(performedBy),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))
                .map(AuditLogDto::from));
    }

    @GetMapping("/entities")
    public List<String> entities() {
        return auditLogRepository.entityNames();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
