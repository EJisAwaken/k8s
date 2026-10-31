package org.esoalahy.stock.core.numerotation;

import jakarta.persistence.*;
import lombok.*;

/**
 * Compteur de numérotation des documents (table de paramétrage du cahier §5) :
 * une ligne par préfixe et par année — « TRF-2026-00042 ».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_sequences", columnNames = {"prefixe", "annee"}))
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String prefixe;

    @Column(nullable = false)
    private int annee;

    @Column(name = "dernier_numero", nullable = false)
    private long dernierNumero;
}
