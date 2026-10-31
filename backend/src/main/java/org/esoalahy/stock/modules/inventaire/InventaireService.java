package org.esoalahy.stock.modules.inventaire;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.export.XlsxExport;
import org.esoalahy.stock.core.export.XlsxExport.Colonne;
import org.esoalahy.stock.core.export.XlsxExport.TypeColonne;
import org.esoalahy.stock.core.numerotation.NumerotationService;
import org.esoalahy.stock.core.user.RoleRules;
import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.catalogue.VarianteRepository;
import org.esoalahy.stock.modules.depot.Depot;
import org.esoalahy.stock.modules.depot.DepotService;
import org.esoalahy.stock.modules.inventaire.dto.*;
import org.esoalahy.stock.modules.stock.Stock;
import org.esoalahy.stock.modules.stock.StockRepository;
import org.esoalahy.stock.modules.stock.StockService;
import org.esoalahy.stock.modules.stock.TypeMouvement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Inventaire complet ou tournant (cahier §3.3) : ouverture (photographie du
 * stock), comptage par saisie ou par scan, soumission, puis validation par
 * l'administrateur qui génère les mouvements d'écart.
 *
 * <p>L'écart se calcule contre la photographie et s'applique en DELTA : une
 * vente faite pendant le comptage, sur un article déjà compté, n'est donc pas
 * « annulée » par l'ajustement.</p>
 */
@Service
@RequiredArgsConstructor
public class InventaireService {

    public static final String DOC_INVENTAIRE = "INVENTAIRE";

    private final InventaireRepository inventaireRepository;
    private final InventaireLigneRepository ligneRepository;
    private final VarianteRepository varianteRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final DepotService depotService;
    private final NumerotationService numerotationService;

    @Transactional(readOnly = true)
    public List<InventaireDto> list(Long depotId, StatutInventaire statut) {
        return inventaireRepository.search(depotId, statut).stream().map(i -> toDto(i, false)).toList();
    }

    @Transactional(readOnly = true)
    public InventaireDto get(Long id) {
        return toDto(require(id), true);
    }

    @Transactional
    public InventaireDto ouvrir(OuvertureInventaireRequest req, Long userId) {
        Depot depot = depotService.requireActif(req.depotId());
        if (inventaireRepository.existsByDepotIdAndStatutIn(depot.getId(),
                List.of(StatutInventaire.EN_COURS, StatutInventaire.A_VALIDER))) {
            throw new ConflictException("Un inventaire est déjà ouvert sur le dépôt « " + depot.getNom() + " ».");
        }
        Inventaire inv = new Inventaire();
        inv.setNumero(numerotationService.prochain("INV"));
        inv.setDepot(depot);
        inv.setType(req.type());
        inv.setCommentaire(req.commentaire() == null || req.commentaire().isBlank() ? null : req.commentaire().trim());
        inv.setOuvertPar(userId);
        inventaireRepository.save(inv);

        if (req.type() == TypeInventaire.COMPLET) {
            for (Stock s : stockRepository.duDepot(depot.getId())) {
                ajouterLigne(inv, s.getVariante(), s.getQtePhysique());
            }
        } else {
            Set<Variante> variantes = new LinkedHashSet<>();
            if (req.varianteIds() != null && !req.varianteIds().isEmpty()) {
                variantes.addAll(varianteRepository.findAllWithProduit(req.varianteIds()));
            }
            if (req.categorieId() != null) {
                variantes.addAll(varianteRepository.activesDeLaCategorie(req.categorieId()));
            }
            // Liste vide permise : les articles s'ajoutent alors au fil du scan.
            for (Variante v : variantes) {
                ajouterLigne(inv, v, physique(v.getId(), depot.getId()));
            }
        }
        return toDto(inv, true);
    }

    /** Saisie d'une quantité comptée ; un article absent de la liste y est ajouté. */
    @Transactional
    public InventaireLigneDto compter(Long id, ComptageRequest req, Long userId) {
        Inventaire inv = requireModifiable(id);
        Variante v = stockService.requireVariante(req.varianteId());
        InventaireLigne ligne = ligneRepository.findByInventaireIdAndVarianteId(id, v.getId())
                .orElseGet(() -> ajouterLigne(inv, v, physique(v.getId(), inv.getDepot().getId())));
        BigDecimal base = req.ajouter() && ligne.getQteComptee() != null ? ligne.getQteComptee() : BigDecimal.ZERO;
        ligne.setQteComptee(base.add(req.quantite()));
        ligne.setComptePar(userId);
        ligne.setCompteAt(Instant.now());
        return toLigneDto(ligne, coutsParVariante(inv), RoleRules.peutVoirLesCouts());
    }

    /** Scan : +1 sur l'article dont le code-barres (ou le SKU) est lu. */
    @Transactional
    public InventaireLigneDto scanner(Long id, String code, Long userId) {
        String c = code == null ? "" : code.trim();
        Variante v = varianteRepository.findByCodeBarres(c)
                .or(() -> varianteRepository.findBySkuIgnoreCase(c))
                .orElseThrow(() -> new NotFoundException("Aucun article ne correspond au code « " + c + " »."));
        return compter(id, new ComptageRequest(v.getId(), BigDecimal.ONE, true), userId);
    }

    @Transactional
    public InventaireDto soumettre(Long id, Long userId) {
        Inventaire inv = requireModifiable(id);
        if (inv.getLignes().stream().noneMatch(l -> l.getQteComptee() != null)) {
            throw new ConflictException("Aucun article n'a été compté.");
        }
        inv.setStatut(StatutInventaire.A_VALIDER);
        inv.setSoumisPar(userId);
        inv.setSoumisAt(Instant.now());
        return toDto(inv, true);
    }

    /** Renvoie un inventaire soumis au comptage (recomptage demandé par l'administrateur). */
    @Transactional
    public InventaireDto rouvrir(Long id) {
        Inventaire inv = require(id);
        if (inv.getStatut() != StatutInventaire.A_VALIDER) {
            throw new ConflictException("Seul un inventaire à valider peut être renvoyé au comptage.");
        }
        inv.setStatut(StatutInventaire.EN_COURS);
        return toDto(inv, true);
    }

    /**
     * Validation par l'administrateur : un mouvement INVENTAIRE_POSITIF ou
     * INVENTAIRE_NEGATIF par écart, sous le numéro de l'inventaire.
     *
     * @param nonComptesAZero vrai → une ligne non comptée vaut 0 (inventaire
     *                        complet : ce qui n'a pas été trouvé n'existe pas) ;
     *                        faux → elle est ignorée.
     */
    @Transactional
    public InventaireDto valider(Long id, boolean nonComptesAZero, Long userId) {
        Inventaire inv = require(id);
        if (inv.getStatut() != StatutInventaire.A_VALIDER && inv.getStatut() != StatutInventaire.EN_COURS) {
            throw new ConflictException("Cet inventaire est déjà " + inv.getStatut().name().toLowerCase() + ".");
        }
        List<StockService.Variation> variations = new ArrayList<>();
        List<String> seriesARegulariser = new ArrayList<>();
        for (InventaireLigne l : inv.getLignes()) {
            if (l.getQteComptee() == null) {
                if (!nonComptesAZero) continue;
                l.setQteComptee(BigDecimal.ZERO);
                l.setCompteAt(Instant.now());
                l.setComptePar(userId);
            }
            BigDecimal ecart = l.ecart();
            if (ecart.signum() == 0) continue;
            if (l.getVariante().getProduit().isSuiviSerie()) {
                seriesARegulariser.add(l.getVariante().libelle());
                continue;
            }
            boolean positif = ecart.signum() > 0;
            variations.add(new StockService.Variation(
                    positif ? TypeMouvement.INVENTAIRE_POSITIF : TypeMouvement.INVENTAIRE_NEGATIF,
                    positif ? 1 : -1, l.getVariante(), inv.getDepot(), ecart.abs(), null, null,
                    "Écart d'inventaire " + inv.getNumero(), DOC_INVENTAIRE, inv.getId(), null));
        }
        if (!seriesARegulariser.isEmpty()) {
            throw new ConflictException("Articles suivis par numéro de série avec un écart : régularisez-les d'abord "
                    + "par une entrée ou une sortie qui précise les numéros, puis recomptez — "
                    + String.join(", ", seriesARegulariser) + ".");
        }
        stockService.appliquerTout(inv.getNumero(), variations, userId);
        inv.setStatut(StatutInventaire.VALIDE);
        inv.setValidePar(userId);
        inv.setValideAt(Instant.now());
        return toDto(inv, true);
    }

    @Transactional
    public InventaireDto annuler(Long id) {
        Inventaire inv = require(id);
        if (inv.getStatut() == StatutInventaire.VALIDE) {
            throw new ConflictException("Un inventaire validé ne s'annule pas : ses écarts sont des mouvements, "
                    + "à corriger par annulation de mouvement.");
        }
        inv.setStatut(StatutInventaire.ANNULE);
        return toDto(inv, true);
    }

    @Transactional(readOnly = true)
    public byte[] exportRapport(Long id) {
        Inventaire inv = require(id);
        boolean couts = RoleRules.peutVoirLesCouts();
        List<Colonne> colonnes = new ArrayList<>(List.of(
                new Colonne("SKU", TypeColonne.TEXTE),
                new Colonne("Article", TypeColonne.TEXTE),
                new Colonne("Théorique", TypeColonne.ENTIER),
                new Colonne("Compté", TypeColonne.ENTIER),
                new Colonne("Écart", TypeColonne.ENTIER)));
        if (couts) colonnes.add(new Colonne("Valeur écart (Ar)", TypeColonne.MONTANT_AR));
        List<List<Object>> lignes = new ArrayList<>();
        for (InventaireLigneDto l : toDto(inv, true).lignes()) {
            List<Object> ligne = new ArrayList<>(Arrays.asList(l.sku(), l.libelle(), l.qteTheorique(), l.qteComptee(), l.ecart()));
            if (couts) ligne.add(l.valeurEcart());
            lignes.add(ligne);
        }
        return XlsxExport.classeur(inv.getNumero(), colonnes, lignes);
    }

    // ── Interne ─────────────────────────────────────────────────────────────

    private InventaireLigne ajouterLigne(Inventaire inv, Variante v, BigDecimal theorique) {
        InventaireLigne l = new InventaireLigne();
        l.setInventaire(inv);
        l.setVariante(v);
        l.setQteTheorique(theorique);
        inv.getLignes().add(l);
        return ligneRepository.save(l);
    }

    private BigDecimal physique(Long varianteId, Long depotId) {
        return stockRepository.trouver(varianteId, depotId).map(Stock::getQtePhysique).orElse(BigDecimal.ZERO);
    }

    private Map<Long, BigDecimal> coutsParVariante(Inventaire inv) {
        Map<Long, BigDecimal> couts = new HashMap<>();
        for (Stock s : stockRepository.duDepot(inv.getDepot().getId())) {
            couts.put(s.getVariante().getId(), s.getCump());
        }
        return couts;
    }

    private InventaireDto toDto(Inventaire inv, boolean details) {
        boolean voirCouts = RoleRules.peutVoirLesCouts();
        Map<Long, BigDecimal> couts = coutsParVariante(inv);
        List<InventaireLigneDto> lignes = (details ? ligneRepository.lignes(inv.getId()) : inv.getLignes())
                .stream().map(l -> toLigneDto(l, couts, voirCouts)).toList();
        long positif = 0;
        long negatif = 0;
        int comptees = 0;
        int ecarts = 0;
        for (InventaireLigneDto l : lignes) {
            if (l.qteComptee() != null) comptees++;
            if (l.ecart() != null && l.ecart().signum() != 0) {
                ecarts++;
                if (l.valeurEcart() != null) {
                    if (l.valeurEcart() > 0) positif += l.valeurEcart(); else negatif += l.valeurEcart();
                }
            }
        }
        return new InventaireDto(inv.getId(), inv.getNumero(), inv.getDepot().getId(), inv.getDepot().getNom(),
                inv.getType().name(), inv.getStatut().name(), inv.getCommentaire(), lignes.size(), comptees, ecarts,
                voirCouts ? positif : null, voirCouts ? negatif : null,
                inv.getCreatedAt(), inv.getCreatedBy(), inv.getValideAt(), details ? lignes : null);
    }

    private static InventaireLigneDto toLigneDto(InventaireLigne l, Map<Long, BigDecimal> couts, boolean voirCouts) {
        BigDecimal ecart = l.ecart();
        Long valeur = null;
        if (voirCouts && ecart != null) {
            valeur = ecart.multiply(couts.getOrDefault(l.getVariante().getId(), BigDecimal.ZERO))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
        }
        Variante v = l.getVariante();
        return new InventaireLigneDto(l.getId(), v.getId(), v.getSku(), v.libelle(), v.getCodeBarres(),
                v.getProduit().isSuiviSerie(), l.getQteTheorique(), l.getQteComptee(), ecart, valeur, l.getCompteAt());
    }

    private Inventaire require(Long id) {
        return inventaireRepository.findById(id).orElseThrow(() -> new NotFoundException("Inventaire introuvable."));
    }

    private Inventaire requireModifiable(Long id) {
        Inventaire inv = require(id);
        if (!inv.estModifiable()) {
            throw new ConflictException("Cet inventaire n'est plus en cours de comptage.");
        }
        return inv;
    }
}
