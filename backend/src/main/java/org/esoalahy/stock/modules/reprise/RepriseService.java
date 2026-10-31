package org.esoalahy.stock.modules.reprise;

import org.esoalahy.stock.core.export.XlsxExport;
import org.esoalahy.stock.core.export.XlsxExport.Colonne;
import org.esoalahy.stock.core.export.XlsxExport.TypeColonne;
import org.esoalahy.stock.core.numerotation.NumerotationService;
import org.esoalahy.stock.modules.catalogue.*;
import org.esoalahy.stock.modules.depot.Depot;
import org.esoalahy.stock.modules.depot.DepotRepository;
import org.esoalahy.stock.modules.depot.Emplacement;
import org.esoalahy.stock.modules.depot.EmplacementRepository;
import org.esoalahy.stock.modules.reprise.LecteurExcel.Ligne;
import org.esoalahy.stock.modules.reprise.RapportImportDto.ErreurLigne;
import org.esoalahy.stock.modules.stock.StockQueryService;
import org.esoalahy.stock.modules.stock.StockService;
import org.esoalahy.stock.modules.stock.TypeMouvement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/**
 * Reprise des données (cahier §7) : import Excel du catalogue, puis du stock
 * initial valorisé. Tout ou rien : la moindre ligne fautive annule l'import
 * entier, et le rapport liste toutes les erreurs d'un coup.
 */
@Service
@RequiredArgsConstructor
public class RepriseService {

    static final List<String> COLONNES_CATALOGUE = List.of(
            "reference", "nom", "categorie", "unite", "nom_fournisseur", "reference_fournisseur",
            "poids_kg", "volume_m3", "code_sh", "suivi_serie",
            "sku", "attributs", "code_barres", "prix_detail", "prix_gros", "prix_revendeur", "seuil_gros", "seuil_mini");

    static final List<String> COLONNES_STOCK = List.of(
            "sku", "depot", "quantite", "cout_unitaire", "emplacement", "numeros_serie");

    private final ProduitRepository produitRepository;
    private final VarianteRepository varianteRepository;
    private final CategorieRepository categorieRepository;
    private final DepotRepository depotRepository;
    private final EmplacementRepository emplacementRepository;
    private final StockService stockService;
    private final StockQueryService stockQueryService;
    private final NumerotationService numerotationService;

    // ── Catalogue ───────────────────────────────────────────────────────────

    @Transactional
    public RapportImportDto importerCatalogue(MultipartFile fichier, boolean simulation) {
        List<Ligne> lignes = LecteurExcel.lire(fichier);
        List<ErreurLigne> erreurs = new ArrayList<>();
        int crees = 0;
        int majs = 0;
        Map<String, Produit> produitsDuFichier = new HashMap<>();
        Set<String> skus = new HashSet<>();

        for (Ligne l : lignes) {
            try {
                String reference = exiger(l, "reference");
                String sku = Optional.ofNullable(l.texte("sku")).orElse(reference);
                if (!skus.add(sku.toLowerCase())) {
                    throw new IllegalArgumentException("SKU « " + sku + " » présent deux fois dans le fichier.");
                }
                Produit p = produitsDuFichier.get(reference.toLowerCase());
                if (p == null) {
                    Optional<Produit> existant = produitRepository.findByReferenceIgnoreCase(reference);
                    p = existant.orElseGet(Produit::new);
                    p.setReference(reference);
                    p.setNom(exiger(l, "nom"));
                    p.setUnite(Optional.ofNullable(l.texte("unite")).orElse("pièce"));
                    p.setNomFournisseur(l.texte("nom_fournisseur"));
                    p.setReferenceFournisseur(l.texte("reference_fournisseur"));
                    p.setPoidsKg(positif(l, "poids_kg"));
                    p.setVolumeM3(positif(l, "volume_m3"));
                    p.setCodeSh(l.texte("code_sh"));
                    p.setSuiviSerie(l.booleen("suivi_serie"));
                    p.setCategorie(categorie(l.texte("categorie")));
                    produitRepository.save(p);
                    produitsDuFichier.put(reference.toLowerCase(), p);
                }

                Optional<Variante> existante = varianteRepository.findBySkuIgnoreCase(sku);
                if (existante.isPresent() && !existante.get().getProduit().getId().equals(p.getId())) {
                    throw new IllegalArgumentException("Le SKU « " + sku + " » appartient déjà au produit "
                            + existante.get().getProduit().getReference() + ".");
                }
                Variante v = existante.orElseGet(Variante::new);
                v.setProduit(p);
                v.setSku(sku);
                v.setAttributs(l.texte("attributs"));
                String code = l.texte("code_barres");
                if (code != null) {
                    varianteRepository.findByCodeBarres(code)
                            .filter(o -> v.getId() == null || !o.getId().equals(v.getId()))
                            .ifPresent(o -> {
                                throw new IllegalArgumentException("Code-barres « " + code + " » déjà attribué à " + o.getSku() + ".");
                            });
                    v.setCodeBarres(code);
                }
                v.setPrixDetail(montant(l, "prix_detail"));
                v.setPrixGros(montant(l, "prix_gros"));
                v.setPrixRevendeur(montant(l, "prix_revendeur"));
                v.setSeuilGros(positif(l, "seuil_gros"));
                v.setSeuilMini(Optional.ofNullable(positif(l, "seuil_mini")).orElse(BigDecimal.ZERO));
                boolean nouvelle = v.getId() == null;
                varianteRepository.saveAndFlush(v);
                if (v.getCodeBarres() == null) v.setCodeBarres(CodesBarres.eanInterne(v.getId()));
                if (nouvelle) {
                    p.getVariantes().add(v);
                    crees++;
                } else {
                    majs++;
                }
            } catch (RuntimeException e) {
                erreurs.add(new ErreurLigne(l.numero(), message(e)));
            }
        }
        return conclure(lignes.size(), crees, majs, null, erreurs, simulation);
    }

    // ── Stock initial ───────────────────────────────────────────────────────

    @Transactional
    public RapportImportDto importerStockInitial(MultipartFile fichier, boolean simulation, Long userId) {
        List<Ligne> lignes = LecteurExcel.lire(fichier);
        List<ErreurLigne> erreurs = new ArrayList<>();
        String numero = numerotationService.prochain("INI");
        Map<String, Depot> depots = new HashMap<>();
        List<StockService.Variation> variations = new ArrayList<>();
        List<Object[]> rangements = new ArrayList<>();

        for (Ligne l : lignes) {
            try {
                String sku = exiger(l, "sku");
                Variante v = varianteRepository.findBySkuIgnoreCase(sku)
                        .orElseThrow(() -> new IllegalArgumentException("SKU « " + sku + " » inconnu : importez d'abord le catalogue."));
                String codeDepot = exiger(l, "depot");
                Depot depot = depots.computeIfAbsent(codeDepot.toLowerCase(), k -> depotRepository.findByCodeIgnoreCase(codeDepot)
                        .filter(Depot::isActif)
                        .orElseThrow(() -> new IllegalArgumentException("Dépôt « " + codeDepot + " » inconnu ou désactivé.")));
                BigDecimal quantite = l.nombre("quantite");
                if (quantite == null || quantite.signum() <= 0) {
                    throw new IllegalArgumentException("La quantité doit être strictement positive.");
                }
                BigDecimal cout = l.nombre("cout_unitaire");
                if (cout == null || cout.signum() < 0) {
                    throw new IllegalArgumentException("Le coût unitaire (en ariary) est obligatoire : le stock initial est valorisé.");
                }
                List<String> series = null;
                String brut = l.texte("numeros_serie");
                if (brut != null) {
                    series = Arrays.stream(brut.split("[;,\\n]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                }
                String codeEmplacement = l.texte("emplacement");
                if (codeEmplacement != null) {
                    Emplacement e = emplacementRepository.findByDepotAndCode(depot.getId(), codeEmplacement)
                            .orElseThrow(() -> new IllegalArgumentException("Emplacement « " + codeEmplacement
                                    + " » inconnu dans le dépôt " + depot.getCode() + "."));
                    rangements.add(new Object[]{v.getId(), depot.getId(), e.getId()});
                }
                variations.add(new StockService.Variation(TypeMouvement.STOCK_INITIAL, 1, v, depot, quantite, cout,
                        series, "Reprise du stock initial", "IMPORT_INITIAL", null, null));
            } catch (RuntimeException e) {
                erreurs.add(new ErreurLigne(l.numero(), message(e)));
            }
        }
        if (erreurs.isEmpty()) {
            try {
                stockService.appliquerTout(numero, variations, userId);
                for (Object[] r : rangements) {
                    stockQueryService.ranger((Long) r[0], (Long) r[1], (Long) r[2]);
                }
            } catch (RuntimeException e) {
                erreurs.add(new ErreurLigne(0, message(e)));
            }
        }
        return conclure(lignes.size(), variations.size(), 0, numero, erreurs, simulation);
    }

    // ── Modèles ─────────────────────────────────────────────────────────────

    public byte[] modeleCatalogue() {
        List<Colonne> colonnes = COLONNES_CATALOGUE.stream().map(c -> new Colonne(c, TypeColonne.TEXTE)).toList();
        return XlsxExport.classeur("Catalogue", colonnes, List.of(
                List.of("CAS-001", "Casque Bluetooth X1", "Audio", "pièce", "蓝牙耳机 X1", "BT-X1", "0.25", "0.002",
                        "8518.30", "non", "CAS-001-NOIR", "Couleur : noir", "", "45000", "38000", "35000", "10", "5"),
                List.of("CAS-001", "Casque Bluetooth X1", "Audio", "pièce", "蓝牙耳机 X1", "BT-X1", "0.25", "0.002",
                        "8518.30", "non", "CAS-001-BLANC", "Couleur : blanc", "", "45000", "38000", "35000", "10", "5")));
    }

    public byte[] modeleStock() {
        List<Colonne> colonnes = COLONNES_STOCK.stream().map(c -> new Colonne(c, TypeColonne.TEXTE)).toList();
        return XlsxExport.classeur("Stock initial", colonnes, List.of(
                List.of("CAS-001-NOIR", "PRINCIPAL", "24", "21500", "A-01", ""),
                List.of("TEL-A15-128", "PRINCIPAL", "2", "520000", "", "356789012345678;356789012345679")));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RapportImportDto conclure(int lus, int crees, int majs, String numero, List<ErreurLigne> erreurs,
                                      boolean simulation) {
        boolean applique = erreurs.isEmpty() && !simulation;
        if (!applique) {
            // Tout ou rien : la transaction est annulée, rien de ce qui précède n'est écrit.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new RapportImportDto(applique, simulation, lus, crees, majs, applique ? numero : null, erreurs);
    }

    private Categorie categorie(String nom) {
        if (nom == null) return null;
        return categorieRepository.findByNomIgnoreCase(nom).orElseGet(() -> {
            Categorie c = new Categorie();
            c.setNom(nom);
            return categorieRepository.save(c);
        });
    }

    private static String exiger(Ligne l, String colonne) {
        String v = l.texte(colonne);
        if (v == null) throw new IllegalArgumentException("La colonne « " + colonne + " » est obligatoire.");
        return v;
    }

    private static BigDecimal positif(Ligne l, String colonne) {
        BigDecimal b = l.nombre(colonne);
        if (b != null && b.signum() < 0) throw new IllegalArgumentException("« " + colonne + " » ne peut pas être négatif.");
        return b;
    }

    private static Long montant(Ligne l, String colonne) {
        Long m = l.entier(colonne);
        if (m != null && m < 0) throw new IllegalArgumentException("« " + colonne + " » ne peut pas être négatif.");
        return m;
    }

    private static String message(RuntimeException e) {
        return e.getMessage() == null ? "Ligne invalide." : e.getMessage();
    }
}
