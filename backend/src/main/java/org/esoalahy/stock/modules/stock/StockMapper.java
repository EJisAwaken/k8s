package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.core.user.RoleRules;
import org.esoalahy.stock.modules.stock.dto.MouvementDto;
import org.esoalahy.stock.modules.stock.dto.StockLigneDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Conversion entités → DTO. C'est ICI que s'applique la règle « le magasinier
 * ne voit ni les coûts d'achat ni les marges » (cahier §2) : les champs de
 * coût et de valeur sortent à {@code null}.
 */
@Component
@RequiredArgsConstructor
public class StockMapper {

    private final MouvementStockRepository mouvementRepository;
    private final NumeroSerieRepository numeroSerieRepository;

    public List<MouvementDto> mouvements(List<MouvementStock> mouvements) {
        if (mouvements.isEmpty()) return List.of();
        boolean couts = RoleRules.peutVoirLesCouts();
        List<Long> ids = mouvements.stream().map(MouvementStock::getId).toList();
        Set<Long> annules = new HashSet<>(mouvementRepository.contrePassesParmi(ids));
        Map<Long, List<String>> series = new HashMap<>();
        for (Object[] row : numeroSerieRepository.numerosDesMouvements(ids)) {
            series.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add((String) row[1]);
        }
        return mouvements.stream().map(m -> new MouvementDto(
                m.getId(),
                m.getNumero(),
                m.getType().name(),
                m.getType().getLabel(),
                m.getSens(),
                m.getVariante().getId(),
                m.getVariante().getSku(),
                m.getVariante().libelle(),
                m.getDepot().getId(),
                m.getDepot().getNom(),
                m.getQuantite(),
                couts ? m.getCoutUnitaire() : null,
                couts ? m.getValeur() : null,
                m.getStockApres(),
                m.getMotif(),
                m.getDocumentType(),
                m.getDocumentId(),
                m.getContrePasseId(),
                annules.contains(m.getId()),
                series.getOrDefault(m.getId(), List.of()),
                m.getCreatedBy(),
                m.getCreatedAt()
        )).toList();
    }

    public StockLigneDto ligne(Stock s) {
        boolean couts = RoleRules.peutVoirLesCouts();
        return new StockLigneDto(
                s.getVariante().getId(),
                s.getVariante().getSku(),
                s.getVariante().libelle(),
                s.getVariante().getProduit().getUnite(),
                s.getDepot().getId(),
                s.getDepot().getNom(),
                s.getEmplacement() == null ? null : s.getEmplacement().getCode(),
                s.getQteCommandee(),
                s.getQteTransit(),
                s.getQtePhysique(),
                s.getQteReservee(),
                s.getQteDisponible(),
                s.getVariante().getSeuilMini(),
                couts ? s.getCump() : null,
                couts ? valeur(s) : null,
                s.getDerniereSortieAt());
    }

    /** Valorisation au CUMP, arrondie à l'ariary. */
    public static long valeur(Stock s) {
        return s.getQtePhysique().multiply(s.getCump()).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static BigDecimal zeroSiNull(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }
}
