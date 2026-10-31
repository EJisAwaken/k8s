package org.esoalahy.stock.modules.inventaire;

import org.esoalahy.stock.modules.catalogue.Variante;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventaire_lignes")
public class InventaireLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventaire_id", nullable = false)
    private Inventaire inventaire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    /** Stock physique photographié à l'ouverture (ou à l'ajout de la ligne). */
    @Column(name = "qte_theorique", nullable = false, precision = 14, scale = 3)
    private BigDecimal qteTheorique;

    @Column(name = "qte_comptee", precision = 14, scale = 3)
    private BigDecimal qteComptee;

    @Column(name = "compte_par")
    private Long comptePar;

    @Column(name = "compte_at")
    private Instant compteAt;

    /** Écart compté − théorique ; {@code null} tant que la ligne n'est pas comptée. */
    public BigDecimal ecart() {
        return qteComptee == null ? null : qteComptee.subtract(qteTheorique);
    }
}
