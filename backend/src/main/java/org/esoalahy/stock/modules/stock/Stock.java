package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.depot.Depot;
import org.esoalahy.stock.modules.depot.Emplacement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quantités d'une variante dans un dépôt (cahier §3.3) et son coût moyen
 * pondéré. Écrit UNIQUEMENT par {@link StockService} à partir des mouvements :
 * il n'y a pas d'endpoint qui modifie ces colonnes directement.
 */
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    /** Emplacement habituel de la variante dans ce dépôt (optionnel). */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emplacement_id")
    private Emplacement emplacement;

    /** En commande fournisseur — alimenté au lot 3. */
    @Column(name = "qte_commandee", nullable = false, precision = 14, scale = 3)
    private BigDecimal qteCommandee = BigDecimal.ZERO;

    /** En transit (payé, pas encore arrivé) — alimenté au lot 3. */
    @Column(name = "qte_transit", nullable = false, precision = 14, scale = 3)
    private BigDecimal qteTransit = BigDecimal.ZERO;

    @Column(name = "qte_physique", nullable = false, precision = 14, scale = 3)
    private BigDecimal qtePhysique = BigDecimal.ZERO;

    /** Réservée par des commandes confirmées — alimenté au lot 2. */
    @Column(name = "qte_reservee", nullable = false, precision = 14, scale = 3)
    private BigDecimal qteReservee = BigDecimal.ZERO;

    /** Coût unitaire moyen pondéré (RG-04), en ariary. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cump = BigDecimal.ZERO;

    @Column(name = "derniere_entree_at")
    private Instant derniereEntreeAt;

    @Column(name = "derniere_sortie_at")
    private Instant derniereSortieAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    Stock(Variante variante, Depot depot) {
        this.variante = variante;
        this.depot = depot;
        this.createdAt = Instant.now();
    }

    /** Disponible = physique − réservée. */
    public BigDecimal getQteDisponible() {
        return qtePhysique.subtract(qteReservee);
    }
}
