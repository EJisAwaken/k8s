package org.esoalahy.stock.core.numerotation;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Numéros de documents sans trou ni doublon : l'incrément est un UPSERT
 * atomique PostgreSQL — deux transactions concurrentes ne peuvent pas lire
 * le même compteur. Exécuté dans la transaction appelante : si l'opération
 * est annulée, le numéro l'est aussi (pas de trou).
 */
@Service
@RequiredArgsConstructor
public class NumerotationService {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String prochain(String prefixe) {
        int annee = LocalDate.now().getYear();
        Number numero = (Number) entityManager.createNativeQuery("""
                INSERT INTO document_sequences (prefixe, annee, dernier_numero)
                VALUES (:prefixe, :annee, 1)
                ON CONFLICT (prefixe, annee)
                DO UPDATE SET dernier_numero = document_sequences.dernier_numero + 1
                RETURNING dernier_numero
                """)
                .setParameter("prefixe", prefixe)
                .setParameter("annee", annee)
                .getSingleResult();
        return String.format("%s-%d-%05d", prefixe, annee, numero.longValue());
    }
}
