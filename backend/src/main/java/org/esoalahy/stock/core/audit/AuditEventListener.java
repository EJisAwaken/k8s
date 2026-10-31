package org.esoalahy.stock.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Journal d'audit générique (cahier §5/§6) branché sur les événements
 * Hibernate : toute création, modification ou suppression d'entité est
 * tracée — qui, quand, valeur avant / valeur après.
 *
 * <p>Les entrées sont mises en tampon pendant la transaction et écrites
 * APRÈS le commit, dans une transaction séparée ({@link AuditWriter}) : une
 * opération annulée ne laisse pas de trace fantôme, et un échec d'écriture
 * du journal ne fait jamais échouer l'opération métier.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    /** Entités jamais journalisées : journaux, jetons, compteurs — bruit, récursion ou secrets. */
    private static final Set<String> EXCLUDED_ENTITIES = Set.of(
            "AuditLog", "RevokedToken", "UserSession", "ConnectionLog", "DocumentSequence");

    /** Champs masqués quel que soit l'entité (motif contenu dans le nom). */
    private static final Set<String> SENSITIVE_FRAGMENTS =
            Set.of("password", "token", "secret", "hash", "recovery");

    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    private static final ThreadLocal<List<AuditLog>> BUFFER = new ThreadLocal<>();

    @Override
    public void onPostInsert(PostInsertEvent event) {
        String entity = simpleName(event.getPersister());
        if (EXCLUDED_ENTITIES.contains(entity)) return;
        Map<String, Object> snapshot = snapshot(event.getState(), event.getPersister().getPropertyNames());
        enqueue(entity, event.getId(), "INSERT", snapshot);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        String entity = simpleName(event.getPersister());
        if (EXCLUDED_ENTITIES.contains(entity)) return;
        Map<String, Object> diff = diff(event.getOldState(), event.getState(),
                event.getPersister().getPropertyNames(), event.getDirtyProperties());
        if (diff.isEmpty()) return;
        enqueue(entity, event.getId(), "UPDATE", diff);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        String entity = simpleName(event.getPersister());
        if (EXCLUDED_ENTITIES.contains(entity)) return;
        Map<String, Object> snapshot = snapshot(event.getDeletedState(), event.getPersister().getPropertyNames());
        enqueue(entity, event.getId(), "DELETE", snapshot);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void enqueue(String entityName, Object id, String action, Map<String, Object> changes) {
        AuditLog entry = AuditLog.builder()
                .entityName(entityName)
                .entityId(id == null ? null : String.valueOf(id))
                .action(action)
                .changes(toJson(changes))
                .performedBy(currentUser())
                .performedAt(Instant.now())
                .build();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                auditWriter.writeAll(List.of(entry));
            } catch (Exception e) {
                log.warn("Échec d'écriture du journal d'audit (hors transaction)", e);
            }
            return;
        }

        List<AuditLog> buffer = BUFFER.get();
        if (buffer == null) {
            buffer = new ArrayList<>();
            BUFFER.set(buffer);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List<AuditLog> toWrite = BUFFER.get();
                    if (toWrite != null && !toWrite.isEmpty()) {
                        try {
                            auditWriter.writeAll(new ArrayList<>(toWrite));
                        } catch (Exception e) {
                            log.warn("Échec d'écriture du journal d'audit", e);
                        }
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    BUFFER.remove();
                }
            });
        }
        buffer.add(entry);
    }

    private Map<String, Object> snapshot(Object[] state, String[] names) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (state == null) return map;
        for (int i = 0; i < names.length; i++) {
            if (isSensitive(names[i])) continue;
            if (isSimple(state[i])) {
                map.put(names[i], stringify(state[i]));
            }
        }
        return map;
    }

    private Map<String, Object> diff(Object[] oldState, Object[] newState, String[] names, int[] dirty) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (newState == null) return map;
        int[] indices = dirty != null ? dirty : allIndices(names.length);
        for (int idx : indices) {
            if (isSensitive(names[idx])) continue;
            Object before = oldState == null ? null : oldState[idx];
            Object after = newState[idx];
            if (!isSimple(before) && !isSimple(after)) continue;
            if (Objects.equals(before, after)) continue;
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("old", stringify(before));
            change.put("new", stringify(after));
            map.put(names[idx], change);
        }
        return map;
    }

    private static int[] allIndices(int length) {
        int[] a = new int[length];
        for (int i = 0; i < length; i++) a[i] = i;
        return a;
    }

    /** N'audite que les valeurs simples : évite de charger des associations lazy. */
    private static boolean isSimple(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof Temporal
                || value instanceof BigDecimal
                || value instanceof java.util.Date;
    }

    private static Object stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSensitive(String propertyName) {
        String lower = propertyName.toLowerCase();
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (lower.contains(fragment)) return true;
        }
        return false;
    }

    private static String simpleName(EntityPersister persister) {
        String name = persister.getEntityName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
