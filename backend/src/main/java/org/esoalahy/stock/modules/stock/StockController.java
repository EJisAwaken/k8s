package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.core.pagination.PageDto;
import org.esoalahy.stock.core.security.CurrentUser;
import org.esoalahy.stock.modules.stock.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock et mouvements. Écriture : administrateur et magasinier ; le
 * commercial ne modifie pas le stock directement (cahier §2) et le comptable
 * est en lecture seule.
 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StockService stockService;
    private final StockQueryService queryService;

    @GetMapping("/types-mouvement")
    public List<TypeMouvementDto> types() {
        return queryService.types();
    }

    @GetMapping
    public List<StockLigneDto> etat(@RequestParam(required = false) Long depotId,
                                    @RequestParam(required = false) Long varianteId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "false") boolean avecStockSeulement) {
        return queryService.etat(depotId, varianteId, q, avecStockSeulement);
    }

    @GetMapping("/alertes")
    public AlertesDto alertes(@RequestParam(required = false) Long depotId) {
        return queryService.alertes(depotId);
    }

    @GetMapping("/mouvements")
    public PageDto<MouvementDto> journal(@RequestParam(required = false) Long depotId,
                                         @RequestParam(required = false) Long varianteId,
                                         @RequestParam(required = false) TypeMouvement type,
                                         @RequestParam(required = false) String numero,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return queryService.journal(depotId, varianteId, type, numero, du, au, page, size);
    }

    @GetMapping("/mouvements/{id}")
    public MouvementDto mouvement(@PathVariable Long id) {
        return queryService.mouvement(id);
    }

    @PostMapping("/mouvements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER')")
    public DocumentMouvementDto mouvement(@Valid @RequestBody MouvementRequest request, @CurrentUser Long userId) {
        return stockService.mouvement(request, userId);
    }

    @PostMapping("/transferts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER')")
    public DocumentMouvementDto transfert(@Valid @RequestBody TransfertRequest request, @CurrentUser Long userId) {
        return stockService.transfert(request, userId);
    }

    /** RG-05 : correction par mouvement inverse. Réservé à l'administrateur. */
    @PostMapping("/mouvements/{id}/annulation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentMouvementDto annuler(@PathVariable Long id, @Valid @RequestBody AnnulationRequest request,
                                        @CurrentUser Long userId) {
        return stockService.annuler(id, request.motif(), userId);
    }

    @GetMapping("/series")
    public List<NumeroSerieDto> series(@RequestParam(required = false) String numero,
                                       @RequestParam(required = false) Long varianteId,
                                       @RequestParam(required = false) Long depotId) {
        return queryService.series(numero, varianteId, depotId);
    }

    @PutMapping("/emplacement")
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER')")
    public StockLigneDto ranger(@RequestParam Long varianteId, @RequestParam Long depotId,
                                @RequestParam(required = false) Long emplacementId) {
        return queryService.ranger(varianteId, depotId, emplacementId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportEtat(@RequestParam(required = false) Long depotId) {
        return fichier(queryService.exportEtat(depotId), "stock-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/mouvements/export")
    public ResponseEntity<byte[]> exportJournal(@RequestParam(required = false) Long depotId,
                                                @RequestParam(required = false) Long varianteId,
                                                @RequestParam(required = false) TypeMouvement type,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return fichier(queryService.exportJournal(depotId, varianteId, type, du, au), "mouvements-" + LocalDate.now() + ".xlsx");
    }

    static ResponseEntity<byte[]> fichier(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .body(contenu);
    }
}
