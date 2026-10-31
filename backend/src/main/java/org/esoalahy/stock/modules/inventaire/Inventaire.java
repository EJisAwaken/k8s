package org.esoalahy.stock.modules.inventaire;

import org.esoalahy.stock.core.jpa.Horodate;
import org.esoalahy.stock.modules.depot.Depot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventaires")
public class Inventaire extends Horodate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeInventaire type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutInventaire statut = StatutInventaire.EN_COURS;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "ouvert_par", nullable = false)
    private Long ouvertPar;

    @Column(name = "soumis_par")
    private Long soumisPar;

    @Column(name = "soumis_at")
    private Instant soumisAt;

    @Column(name = "valide_par")
    private Long validePar;

    @Column(name = "valide_at")
    private Instant valideAt;

    @OneToMany(mappedBy = "inventaire", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventaireLigne> lignes = new ArrayList<>();

    public boolean estModifiable() {
        return statut == StatutInventaire.EN_COURS;
    }
}
