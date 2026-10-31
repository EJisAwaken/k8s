package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.export.XlsxExport;
import org.esoalahy.stock.core.export.XlsxExport.Colonne;
import org.esoalahy.stock.core.export.XlsxExport.TypeColonne;
import org.esoalahy.stock.core.pagination.PageDto;
import org.esoalahy.stock.core.user.RoleRules;
import org.esoalahy.stock.modules.depot.Emplacement;
import org.esoalahy.stock.modules.depot.EmplacementRepository;
import org.esoalahy.stock.modules.stock.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Consultation du stock : état, journal, alertes, numéros de série, exports. */
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private static final Instant DEBUT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FIN = Instant.parse("2999-12-31T00:00:00Z");

    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final NumeroSerieRepository numeroSerieRepository;
    private final EmplacementRepository emplacementRepository;
    private final StockMapper mapper;

    @Value("${app.stock.dormant-days:90}")
    private int joursDormance;

    public List<TypeMouvementDto> types() {
        return Arrays.stream(TypeMouvement.values())
                .map(t -> new TypeMouvementDto(t.name(), t.getLabel(), t.getSens(), t.isSaisieDirecte(), t.exigeUnMotif()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLigneDto> etat(Long depotId, Long varianteId, String q, boolean avecStockSeulement) {
        return stockRepository.etat(depotId, varianteId, like(q), exact(q), avecStockSeulement)
                .stream().map(mapper::ligne).toList();
    }

    @Transactional(readOnly = true)
    public PageDto<MouvementDto> journal(Long depotId, Long varianteId, TypeMouvement type, String numero,
                                         LocalDate du, LocalDate au, int page, int size) {
        Page<MouvementStock> p = mouvementRepository.journal(depotId, varianteId, type,
                numero == null || numero.isBlank() ? null : numero.trim().toUpperCase(),
                du == null ? DEBUT : du.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                au == null ? FIN : au.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500)));
        List<MouvementDto> contenu = mapper.mouvements(p.getContent());
        return new PageDto<>(contenu, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MouvementDto mouvement(Long id) {
        MouvementStock m = mouvementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mouvement introuvable."));
        return mapper.mouvements(List.of(m)).get(0);
    }

    @Transactional(readOnly = true)
    public AlertesDto alertes(Long depotId) {
        List<StockLigneDto> ruptures = new ArrayList<>();
        List<StockLigneDto> sousSeuil = new ArrayList<>();
        for (Stock s : stockRepository.sousSeuil(depotId)) {
            (s.getQteDisponible().signum() <= 0 ? ruptures : sousSeuil).add(mapper.ligne(s));
        }
        Instant depuis = Instant.now().minus(joursDormance, ChronoUnit.DAYS);
        List<StockLigneDto> dormants = stockRepository.dormants(depotId, depuis).stream().map(mapper::ligne).toList();
        return new AlertesDto(ruptures, sousSeuil, dormants, joursDormance);
    }

    @Transactional(readOnly = true)
    public List<NumeroSerieDto> series(String numero, Long varianteId, Long depotId) {
        List<NumeroSerie> trouves;
        if (numero != null && !numero.isBlank()) {
            trouves = numeroSerieRepository.findByNumero(numero.trim());
        } else if (varianteId != null && depotId != null) {
            trouves = numeroSerieRepository.findByVarianteIdAndDepotIdAndStatutOrderByNumeroAsc(
                    varianteId, depotId, StatutSerie.EN_STOCK);
        } else {
            throw new IllegalArgumentException("Indiquez un numéro de série, ou un article et un dépôt.");
        }
        return trouves.stream().map(n -> new NumeroSerieDto(n.getId(), n.getNumero(), n.getVariante().getId(),
                n.getVariante().getSku(), n.getVariante().libelle(), n.getStatut().name(),
                n.getDepot() == null ? null : n.getDepot().getId(),
                n.getDepot() == null ? null : n.getDepot().getNom())).toList();
    }

    /** Range une variante à un emplacement de son dépôt (sans effet sur les quantités). */
    @Transactional
    public StockLigneDto ranger(Long varianteId, Long depotId, Long emplacementId) {
        Stock stock = stockRepository.trouver(varianteId, depotId)
                .orElseThrow(() -> new NotFoundException("Cet article n'a pas encore de stock dans ce dépôt."));
        Emplacement e = null;
        if (emplacementId != null) {
            e = emplacementRepository.findById(emplacementId)
                    .filter(x -> x.getDepot().getId().equals(depotId))
                    .orElseThrow(() -> new NotFoundException("Emplacement introuvable dans ce dépôt."));
        }
        stock.setEmplacement(e);
        return mapper.ligne(stock);
    }

    @Transactional(readOnly = true)
    public byte[] exportEtat(Long depotId) {
        boolean couts = RoleRules.peutVoirLesCouts();
        List<Colonne> colonnes = new ArrayList<>(List.of(
                new Colonne("SKU", TypeColonne.TEXTE),
                new Colonne("Article", TypeColonne.TEXTE),
                new Colonne("Dépôt", TypeColonne.TEXTE),
                new Colonne("Emplacement", TypeColonne.TEXTE),
                new Colonne("Commandé", TypeColonne.ENTIER),
                new Colonne("En transit", TypeColonne.ENTIER),
                new Colonne("Physique", TypeColonne.ENTIER),
                new Colonne("Réservé", TypeColonne.ENTIER),
                new Colonne("Disponible", TypeColonne.ENTIER)));
        if (couts) {
            colonnes.add(new Colonne("CUMP (Ar)", TypeColonne.MONTANT_AR));
            colonnes.add(new Colonne("Valeur (Ar)", TypeColonne.MONTANT_AR));
        }
        List<List<Object>> lignes = new ArrayList<>();
        for (StockLigneDto l : etat(depotId, null, null, false)) {
            List<Object> ligne = new ArrayList<>(Arrays.asList(l.sku(), l.libelle(), l.depotNom(), l.emplacementCode(),
                    l.qteCommandee(), l.qteTransit(), l.qtePhysique(), l.qteReservee(), l.qteDisponible()));
            if (couts) {
                ligne.add(l.cump() == null ? null : l.cump().setScale(0, java.math.RoundingMode.HALF_UP));
                ligne.add(l.valeur());
            }
            lignes.add(ligne);
        }
        return XlsxExport.classeur("Stock", colonnes, lignes);
    }

    @Transactional(readOnly = true)
    public byte[] exportJournal(Long depotId, Long varianteId, TypeMouvement type, LocalDate du, LocalDate au) {
        boolean couts = RoleRules.peutVoirLesCouts();
        List<Colonne> colonnes = new ArrayList<>(List.of(
                new Colonne("Date", TypeColonne.DATE_HEURE),
                new Colonne("N° document", TypeColonne.TEXTE),
                new Colonne("Type", TypeColonne.TEXTE),
                new Colonne("SKU", TypeColonne.TEXTE),
                new Colonne("Article", TypeColonne.TEXTE),
                new Colonne("Dépôt", TypeColonne.TEXTE),
                new Colonne("Quantité (±)", TypeColonne.ENTIER),
                new Colonne("Stock après", TypeColonne.ENTIER),
                new Colonne("Motif", TypeColonne.TEXTE),
                new Colonne("Utilisateur", TypeColonne.TEXTE)));
        if (couts) {
            colonnes.add(new Colonne("Coût unitaire (Ar)", TypeColonne.MONTANT_AR));
            colonnes.add(new Colonne("Valeur (Ar)", TypeColonne.MONTANT_AR));
        }
        List<List<Object>> lignes = new ArrayList<>();
        for (MouvementDto m : journal(depotId, varianteId, type, null, du, au, 0, 500).content()) {
            List<Object> ligne = new ArrayList<>(Arrays.asList(m.createdAt(), m.numero(), m.typeLabel(), m.sku(),
                    m.libelle(), m.depotNom(), m.quantite().multiply(BigDecimal.valueOf(m.sens())), m.stockApres(),
                    m.motif(), m.createdBy()));
            if (couts) {
                ligne.add(m.coutUnitaire() == null ? null : m.coutUnitaire().setScale(0, java.math.RoundingMode.HALF_UP));
                ligne.add(m.valeur() == null ? null : m.valeur() * m.sens());
            }
            lignes.add(ligne);
        }
        return XlsxExport.classeur("Mouvements", colonnes, lignes);
    }

    static String like(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
    }

    static String exact(String q) {
        // Jamais null : une chaîne vide ne correspond à aucun code-barres.
        return q == null ? "" : q.trim();
    }
}
