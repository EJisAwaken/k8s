package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.depot.Depot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mouvement de stock — IMMUABLE (RG-05) : {@link Immutable} côté Hibernate,
 * trigger {@code trg_mouvements_immuables} côté base. Une erreur se corrige
 * par un mouvement {@link TypeMouvement#ANNULATION} qui pointe vers celui-ci.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Immutable
@Table(name = "mouvements_stock")
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro du document qui a produit le mouvement (MVT-, TRF-, INV-, INI-…). */
    @Column(nullable = false, length = 30)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeMouvement type;

    /** +1 entrée, −1 sortie. */
    @Column(nullable = false)
    private short sens;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(name = "cout_unitaire", nullable = false, precision = 18, scale = 4)
    private BigDecimal coutUnitaire;

    /** Valeur du mouvement en ariary (quantité × coût unitaire, arrondie). */
    @Column(nullable = false)
    private long valeur;

    @Column(name = "stock_apres", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockApres;

    @Column(name = "cump_apres", nullable = false, precision = 18, scale = 4)
    private BigDecimal cumpApres;

    @Column(length = 500)
    private String motif;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "contre_passe_id")
    private Long contrePasseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean estEntree() {
        return sens > 0;
    }
}
