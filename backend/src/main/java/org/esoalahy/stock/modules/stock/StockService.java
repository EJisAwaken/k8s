package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.ForbiddenException;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.numerotation.NumerotationService;
import org.esoalahy.stock.core.user.RoleRules;
import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.catalogue.VarianteRepository;
import org.esoalahy.stock.modules.depot.Depot;
import org.esoalahy.stock.modules.depot.DepotService;
import org.esoalahy.stock.modules.stock.dto.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Seul point d'écriture du stock (cahier §4 : « le stock n'est jamais saisi à
 * la main »). Toute variation passe par {@link #appliquer} qui, dans la même
 * transaction :
 * <ol>
 *   <li>verrouille la ligne (variante, dépôt) — {@code SELECT … FOR UPDATE} ;</li>
 *   <li>contrôle RG-06 (jamais de physique négatif) ;</li>
 *   <li>recalcule le CUMP sur les entrées (RG-04) ;</li>
 *   <li>écrit le mouvement immuable (RG-05) et met à jour les numéros de série.</li>
 * </ol>
 *
 * <p>Ordre de verrouillage : quand une opération touche plusieurs lignes,
 * elles sont traitées triées par (variante, dépôt) pour éviter les
 * interblocages entre deux opérations concurrentes.</p>
 */
@Service
@RequiredArgsConstructor
public class StockService {

    public static final String DOC_MOUVEMENT = "MOUVEMENT";
    public static final String DOC_TRANSFERT = "TRANSFERT";
    public static final String DOC_ANNULATION = "ANNULATION";

    private static final int ECHELLE_CUMP = 4;

    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final NumeroSerieRepository numeroSerieRepository;
    private final VarianteRepository varianteRepository;
    private final DepotService depotService;
    private final NumerotationService numerotationService;
    private final EntityManager entityManager;
    private final StockMapper mapper;

    /** Une variation élémentaire, avant application. */
    public record Variation(TypeMouvement type, int sens, Variante variante, Depot depot,
                            BigDecimal quantite, BigDecimal coutUnitaire, List<String> numerosSerie,
                            String motif, String documentType, Long documentId, Long contrePasseId) {
    }

    // ── Opérations publiques ────────────────────────────────────────────────

    /** Mouvement saisi directement (entrée, sortie, ajustement, casse, retour). */
    @Transactional
    public DocumentMouvementDto mouvement(MouvementRequest req, Long userId) {
        TypeMouvement type = req.type();
        if (!type.isSaisieDirecte() || type == TypeMouvement.STOCK_INITIAL) {
            throw new IllegalArgumentException("Le type « " + type.getLabel() + " » ne se saisit pas directement.");
        }
        String motif = normaliserMotif(req.motif());
        if (type.exigeUnMotif() && motif == null) {
            throw new IllegalArgumentException("Un motif est obligatoire pour « " + type.getLabel() + " ».");
        }
        Depot depot = depotService.requireActif(req.depotId());
        String numero = numerotationService.prochain("MVT");

        List<Variation> variations = new ArrayList<>();
        for (LigneMouvementRequest l : req.lignes()) {
            BigDecimal cout = null;
            if (l.coutUnitaire() != null) {
                if (type.getSens() < 0) {
                    throw new IllegalArgumentException("Une sortie se valorise au coût moyen : ne saisissez pas de coût.");
                }
                if (!RoleRules.peutVoirLesCouts()) {
                    throw new ForbiddenException("Votre profil ne peut pas saisir de coût d'achat.");
                }
                cout = l.coutUnitaire();
            }
            variations.add(new Variation(type, type.getSens(), requireVariante(l.varianteId()), depot,
                    l.quantite(), cout, l.numerosSerie(), motif, DOC_MOUVEMENT, null, null));
        }
        return document(numero, appliquerTout(numero, variations, userId));
    }

    /** Transfert entre dépôts : sortie au CUMP du départ, entrée au même coût à l'arrivée. */
    @Transactional
    public DocumentMouvementDto transfert(TransfertRequest req, Long userId) {
        if (req.depotSourceId().equals(req.depotDestinationId())) {
            throw new IllegalArgumentException("Les dépôts de départ et d'arrivée doivent être différents.");
        }
        Depot source = depotService.requireActif(req.depotSourceId());
        Depot destination = depotService.requireActif(req.depotDestinationId());
        String motif = normaliserMotif(req.motif());
        String numero = numerotationService.prochain("TRF");

        // Les sorties d'abord (elles fixent le coût), triées pour l'ordre de verrouillage.
        List<LigneMouvementRequest> lignes = new ArrayList<>(req.lignes());
        lignes.sort(Comparator.comparing(LigneMouvementRequest::varianteId));
        List<Variante> variantes = lignes.stream().map(l -> requireVariante(l.varianteId())).toList();
        List<Cle> cles = new ArrayList<>();
        variantes.forEach(v -> {
            cles.add(new Cle(v, source));
            cles.add(new Cle(v, destination));
        });
        verrouillerDansLOrdre(cles);

        List<MouvementStock> produits = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            LigneMouvementRequest l = lignes.get(i);
            if (l.coutUnitaire() != null) {
                throw new IllegalArgumentException("Un transfert se valorise au coût moyen du dépôt de départ.");
            }
            Variante v = variantes.get(i);
            MouvementStock sortie = appliquer(new Variation(TypeMouvement.TRANSFERT_SORTIE, -1, v, source,
                    l.quantite(), null, l.numerosSerie(), motif, DOC_TRANSFERT, null, null), numero, userId);
            MouvementStock entree = appliquer(new Variation(TypeMouvement.TRANSFERT_ENTREE, 1, v, destination,
                    l.quantite(), sortie.getCoutUnitaire(), l.numerosSerie(), motif, DOC_TRANSFERT, null, null),
                    numero, userId);
            produits.add(sortie);
            produits.add(entree);
        }
        return document(numero, produits);
    }

    /**
     * RG-05 : corrige une erreur par un mouvement inverse. Un transfert
     * s'annule en entier (ses deux jambes), en commençant par l'arrivée.
     */
    @Transactional
    public DocumentMouvementDto annuler(Long mouvementId, String motif, Long userId) {
        MouvementStock origine = mouvementRepository.findById(mouvementId)
                .orElseThrow(() -> new NotFoundException("Mouvement introuvable."));
        if (origine.getType() == TypeMouvement.ANNULATION) {
            throw new ConflictException("Une annulation ne s'annule pas : saisissez un nouveau mouvement.");
        }
        String motifPropre = normaliserMotif(motif);
        if (motifPropre == null) {
            throw new IllegalArgumentException("Un motif est obligatoire pour annuler un mouvement.");
        }

        List<MouvementStock> aAnnuler = DOC_TRANSFERT.equals(origine.getDocumentType())
                ? mouvementRepository.findByNumeroOrderByIdAsc(origine.getNumero()).stream()
                        .filter(m -> m.getType() != TypeMouvement.ANNULATION)
                        .filter(m -> sameVariante(m, origine))
                        .toList()
                : List.of(origine);

        for (MouvementStock m : aAnnuler) {
            if (mouvementRepository.existsByContrePasseId(m.getId())) {
                throw new ConflictException("Le mouvement " + m.getNumero() + " a déjà été annulé.");
            }
        }

        verrouillerDansLOrdre(aAnnuler.stream().map(m -> new Cle(m.getVariante(), m.getDepot())).toList());
        String numero = numerotationService.prochain("ANN");
        // Les entrées d'abord : on retire la marchandise avant de la remettre ailleurs.
        List<MouvementStock> ordonnes = new ArrayList<>(aAnnuler);
        ordonnes.sort(Comparator.comparing((MouvementStock m) -> m.estEntree() ? 0 : 1));
        List<MouvementStock> produits = new ArrayList<>();
        for (MouvementStock m : ordonnes) {
            List<String> series = numeroSerieRepository.duMouvement(m.getId()).stream()
                    .map(NumeroSerie::getNumero).toList();
            produits.add(appliquer(new Variation(TypeMouvement.ANNULATION, -m.getSens(), m.getVariante(), m.getDepot(),
                    m.getQuantite(), m.getCoutUnitaire(), series.isEmpty() ? null : series,
                    "Annulation de " + m.getNumero() + " : " + motifPropre, DOC_ANNULATION, m.getId(), m.getId()),
                    numero, userId));
        }
        return document(numero, produits);
    }

    // ── Moteur ──────────────────────────────────────────────────────────────

    /**
     * Applique un lot de variations sous un même numéro, dans l'ordre de
     * verrouillage (variante, dépôt). Réservé aux modules du stock
     * (inventaire, reprise de données) — la transaction appelante est requise.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MouvementStock> appliquerTout(String numero, List<Variation> variations, Long userId) {
        List<Variation> triees = new ArrayList<>(variations);
        triees.sort(Comparator.comparing((Variation v) -> v.variante().getId())
                .thenComparing(v -> v.depot().getId()));
        verrouillerDansLOrdre(triees.stream().map(v -> new Cle(v.variante(), v.depot())).toList());
        List<MouvementStock> produits = new ArrayList<>();
        for (Variation v : triees) {
            produits.add(appliquer(v, numero, userId));
        }
        return produits;
    }

    private MouvementStock appliquer(Variation v, String numero, Long userId) {
        BigDecimal quantite = v.quantite();
        if (quantite == null || quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive.");
        }
        Variante variante = v.variante();
        if (variante.isArchive() || variante.getProduit().isArchive()) {
            if (v.sens() > 0 && v.type() != TypeMouvement.ANNULATION && v.type() != TypeMouvement.RETOUR) {
                throw new ConflictException("« " + variante.libelle() + " » est archivé : aucune entrée n'est possible.");
            }
        }
        List<String> series = controlerSeries(variante, quantite, v.numerosSerie());

        Stock stock = verrouillerOuCreer(variante, v.depot());
        BigDecimal physique = stock.getQtePhysique();
        BigDecimal cump = stock.getCump();
        BigDecimal cout;
        BigDecimal nouveauPhysique;
        BigDecimal nouveauCump = cump;
        Instant maintenant = Instant.now();

        if (v.sens() > 0) {
            cout = v.coutUnitaire() != null ? v.coutUnitaire() : cump;
            nouveauPhysique = physique.add(quantite);
            // RG-04 : CUMP = (stock × CUMP + quantité × coût) / (stock + quantité)
            nouveauCump = physique.signum() == 0
                    ? cout
                    : physique.multiply(cump).add(quantite.multiply(cout))
                            .divide(nouveauPhysique, ECHELLE_CUMP, RoundingMode.HALF_UP);
            stock.setDerniereEntreeAt(maintenant);
        } else {
            boolean controleDisponible = v.type() != TypeMouvement.ANNULATION
                    && v.type() != TypeMouvement.INVENTAIRE_NEGATIF;
            BigDecimal plafond = controleDisponible ? stock.getQteDisponible() : physique;
            // RG-06 : le stock physique ne peut jamais devenir négatif.
            if (quantite.compareTo(plafond) > 0) {
                throw new ConflictException(String.format(
                        "Stock insuffisant pour « %s » au dépôt %s : %s disponible, %s demandé.",
                        variante.libelle(), v.depot().getNom(), plain(plafond), plain(quantite)));
            }
            nouveauPhysique = physique.subtract(quantite);
            if (v.type() == TypeMouvement.ANNULATION && v.coutUnitaire() != null) {
                // Annulation d'une entrée : on retire la marchandise à SON coût d'origine.
                cout = v.coutUnitaire();
                if (nouveauPhysique.signum() > 0) {
                    BigDecimal reste = physique.multiply(cump).subtract(quantite.multiply(cout));
                    nouveauCump = reste.signum() <= 0
                            ? BigDecimal.ZERO
                            : reste.divide(nouveauPhysique, ECHELLE_CUMP, RoundingMode.HALF_UP);
                }
            } else {
                cout = cump;
            }
            if (v.type() == TypeMouvement.SORTIE) {
                stock.setDerniereSortieAt(maintenant);
            }
        }

        stock.setQtePhysique(nouveauPhysique);
        stock.setCump(nouveauCump.setScale(ECHELLE_CUMP, RoundingMode.HALF_UP));
        stock.setUpdatedAt(maintenant);

        MouvementStock mouvement = mouvementRepository.save(MouvementStock.builder()
                .numero(numero)
                .type(v.type())
                .sens((short) v.sens())
                .variante(variante)
                .depot(v.depot())
                .quantite(quantite)
                .coutUnitaire(cout.setScale(ECHELLE_CUMP, RoundingMode.HALF_UP))
                .valeur(quantite.multiply(cout).setScale(0, RoundingMode.HALF_UP).longValueExact())
                .stockApres(nouveauPhysique)
                .cumpApres(stock.getCump())
                .motif(v.motif())
                .documentType(v.documentType())
                .documentId(v.documentId())
                .contrePasseId(v.contrePasseId())
                .userId(userId)
                .createdBy(currentUsername())
                .createdAt(maintenant)
                .build());

        if (!series.isEmpty()) {
            mettreAJourSeries(mouvement, series, v);
        }
        return mouvement;
    }

    private record Cle(Variante variante, Depot depot) {
    }

    /**
     * Prend tous les verrous d'une opération AVANT de modifier quoi que ce
     * soit, dans un ordre global (variante, dépôt) : deux transferts croisés
     * A→B et B→A sur le même article ne peuvent pas s'interbloquer.
     */
    private void verrouillerDansLOrdre(Collection<Cle> cles) {
        cles.stream()
                .sorted(Comparator.comparing((Cle c) -> c.variante().getId()).thenComparing(c -> c.depot().getId()))
                .distinct()
                .forEach(c -> verrouillerOuCreer(c.variante(), c.depot()));
    }

    /**
     * Verrouille la ligne de stock, en la créant au besoin. La création passe
     * par un {@code INSERT … ON CONFLICT DO NOTHING} : deux premières entrées
     * concurrentes sur la même (variante, dépôt) ne se marchent pas dessus.
     */
    private Stock verrouillerOuCreer(Variante variante, Depot depot) {
        Optional<Stock> existant = stockRepository.verrouiller(variante.getId(), depot.getId());
        if (existant.isPresent()) {
            return existant.get();
        }
        entityManager.createNativeQuery("""
                INSERT INTO stocks (variante_id, depot_id, created_at)
                VALUES (:varianteId, :depotId, now())
                ON CONFLICT (variante_id, depot_id) DO NOTHING
                """)
                .setParameter("varianteId", variante.getId())
                .setParameter("depotId", depot.getId())
                .executeUpdate();
        return stockRepository.verrouiller(variante.getId(), depot.getId())
                .orElseThrow(() -> new IllegalStateException("Ligne de stock introuvable après création."));
    }

    private List<String> controlerSeries(Variante variante, BigDecimal quantite, List<String> numeros) {
        if (!variante.getProduit().isSuiviSerie()) {
            if (numeros != null && !numeros.isEmpty()) {
                throw new IllegalArgumentException("« " + variante.libelle() + " » n'est pas suivi par numéro de série.");
            }
            return List.of();
        }
        if (quantite.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Un article suivi par numéro de série se compte à l'unité.");
        }
        List<String> propres = numeros == null ? List.of() : numeros.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (propres.size() != quantite.intValueExact()) {
            throw new IllegalArgumentException(String.format(
                    "« %s » : %d numéro(s) de série saisi(s) pour une quantité de %s.",
                    variante.libelle(), propres.size(), plain(quantite)));
        }
        if (new HashSet<>(propres).size() != propres.size()) {
            throw new IllegalArgumentException("Un même numéro de série est saisi deux fois.");
        }
        return propres;
    }

    private void mettreAJourSeries(MouvementStock mouvement, List<String> numeros, Variation v) {
        Variante variante = v.variante();
        Depot depot = v.depot();
        Instant maintenant = Instant.now();
        for (String numero : numeros) {
            Optional<NumeroSerie> existant = numeroSerieRepository.findByVarianteIdAndNumero(variante.getId(), numero);
            NumeroSerie serie;
            if (v.sens() > 0) {
                if (existant.isPresent() && existant.get().getStatut() == StatutSerie.EN_STOCK) {
                    throw new ConflictException("Le numéro de série " + numero + " est déjà en stock.");
                }
                serie = existant.orElseGet(() -> {
                    NumeroSerie n = new NumeroSerie();
                    n.setVariante(variante);
                    n.setNumero(numero);
                    return n;
                });
                serie.setStatut(StatutSerie.EN_STOCK);
                serie.setDepot(depot);
            } else {
                serie = existant
                        .filter(n -> n.getStatut() == StatutSerie.EN_STOCK && n.getDepot() != null
                                && n.getDepot().getId().equals(depot.getId()))
                        .orElseThrow(() -> new ConflictException(
                                "Le numéro de série " + numero + " n'est pas en stock au dépôt " + depot.getNom() + "."));
                serie.setStatut(v.type() == TypeMouvement.CASSE ? StatutSerie.CASSE : StatutSerie.SORTI);
                serie.setDepot(null);
            }
            serie.setUpdatedAt(maintenant);
            serie = numeroSerieRepository.save(serie);
            numeroSerieRepository.lier(mouvement.getId(), serie.getId());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    public Variante requireVariante(Long id) {
        return varianteRepository.findById(id).orElseThrow(() -> new NotFoundException("Article introuvable."));
    }

    private DocumentMouvementDto document(String numero, List<MouvementStock> mouvements) {
        return new DocumentMouvementDto(numero, mapper.mouvements(mouvements));
    }

    private static boolean sameVariante(MouvementStock a, MouvementStock b) {
        return a.getVariante().getId().equals(b.getVariante().getId());
    }

    private static String normaliserMotif(String motif) {
        return motif == null || motif.isBlank() ? null : motif.trim();
    }

    static String plain(BigDecimal b) {
        return b.stripTrailingZeros().toPlainString();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
}
