package org.esoalahy.stock.modules.catalogue;

import org.esoalahy.stock.core.jpa.Horodate;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiche produit (cahier §3.1). Le stock vit sur ses {@link Variante}s : un
 * produit sans déclinaison a une variante unique.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "produits")
public class Produit extends Horodate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String reference;

    @Column(nullable = false, length = 200)
    private String nom;

    /** Nom chinois ou nom chez le fournisseur. */
    @Column(name = "nom_fournisseur", length = 255)
    private String nomFournisseur;

    @Column(name = "reference_fournisseur", length = 120)
    private String referenceFournisseur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @Column(nullable = false, length = 20)
    private String unite = "pièce";

    /** Poids et volume unitaires : servent à répartir le fret (lot 3). */
    @Column(name = "poids_kg", precision = 12, scale = 3)
    private BigDecimal poidsKg;

    @Column(name = "volume_m3", precision = 12, scale = 6)
    private BigDecimal volumeM3;

    @Column(name = "code_sh", length = 20)
    private String codeSh;

    /** Suivi par numéro de série / IMEI (téléphones, électronique). */
    @Column(name = "suivi_serie", nullable = false)
    private boolean suiviSerie;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** RG-01 : un produit qui a des mouvements s'archive, il ne se supprime pas. */
    @Column(nullable = false)
    private boolean archive;

    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL)
    @OrderBy("sku ASC")
    private List<Variante> variantes = new ArrayList<>();
}
