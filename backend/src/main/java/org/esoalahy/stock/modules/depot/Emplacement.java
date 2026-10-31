package org.esoalahy.stock.modules.depot;

import org.esoalahy.stock.core.jpa.Horodate;
import jakarta.persistence.*;
import lombok.*;

/** Emplacement optionnel dans un dépôt (rayon, étagère). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "emplacements")
public class Emplacement extends Horodate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 120)
    private String libelle;

    @Column(nullable = false)
    private boolean actif = true;
}
