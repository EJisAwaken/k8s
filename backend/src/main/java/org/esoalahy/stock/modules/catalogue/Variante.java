package org.esoalahy.stock.modules.catalogue;

import org.esoalahy.stock.core.jpa.Horodate;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Déclinaison d'un produit (couleur, taille, modèle) avec son propre stock,
 * son code-barres et sa grille de prix (détail, gros, revendeur).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "variantes")
public class Variante extends Horodate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(nullable = false, length = 60)
    private String sku;

    /** Libellé libre : « Couleur : noir · Taille : M ». */
    @Column(length = 255)
    private String attributs;

    @Column(name = "code_barres", length = 64)
    private String codeBarres;

    @Column(name = "prix_detail")
    private Long prixDetail;

    @Column(name = "prix_gros")
    private Long prixGros;

    @Column(name = "prix_revendeur")
    private Long prixRevendeur;

    /** Quantité à partir de laquelle le prix de gros s'applique. */
    @Column(name = "seuil_gros", precision = 14, scale = 3)
    private BigDecimal seuilGros;

    /** Seuil d'alerte de réapprovisionnement. */
    @Column(name = "seuil_mini", nullable = false, precision = 14, scale = 3)
    private BigDecimal seuilMini = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean archive;

    /** « Nom du produit — attributs », tel qu'affiché sur les listes et étiquettes. */
    public String libelle() {
        return attributs == null || attributs.isBlank()
                ? produit.getNom()
                : produit.getNom() + " — " + attributs;
    }
}
