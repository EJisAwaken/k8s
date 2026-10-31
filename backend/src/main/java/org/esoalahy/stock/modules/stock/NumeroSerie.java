package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.depot.Depot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Numéro de série ou IMEI d'un article suivi unitairement (garantie). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "numeros_serie")
public class NumeroSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @Column(nullable = false, length = 80)
    private String numero;

    /** Dépôt où se trouve l'article ; {@code null} une fois sorti ou cassé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutSerie statut;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
