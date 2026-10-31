package org.esoalahy.stock.modules.inventaire;

import org.esoalahy.stock.core.security.CurrentUser;
import org.esoalahy.stock.modules.inventaire.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Inventaires : comptage par le magasinier, validation des écarts par l'administrateur. */
@RestController
@RequestMapping("/api/v1/inventaires")
@RequiredArgsConstructor
public class InventaireController {

    private static final String COMPTAGE = "hasAnyRole('ADMIN','MAGASINIER')";

    private final InventaireService inventaireService;

    @GetMapping
    public List<InventaireDto> list(@RequestParam(required = false) Long depotId,
                                    @RequestParam(required = false) StatutInventaire statut) {
        return inventaireService.list(depotId, statut);
    }

    @GetMapping("/{id}")
    public InventaireDto get(@PathVariable Long id) {
        return inventaireService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(COMPTAGE)
    public InventaireDto ouvrir(@Valid @RequestBody OuvertureInventaireRequest request, @CurrentUser Long userId) {
        return inventaireService.ouvrir(request, userId);
    }

    @PostMapping("/{id}/comptages")
    @PreAuthorize(COMPTAGE)
    public InventaireLigneDto compter(@PathVariable Long id, @Valid @RequestBody ComptageRequest request,
                                      @CurrentUser Long userId) {
        return inventaireService.compter(id, request, userId);
    }

    @PostMapping("/{id}/scan/{code}")
    @PreAuthorize(COMPTAGE)
    public InventaireLigneDto scanner(@PathVariable Long id, @PathVariable String code, @CurrentUser Long userId) {
        return inventaireService.scanner(id, code, userId);
    }

    @PostMapping("/{id}/soumission")
    @PreAuthorize(COMPTAGE)
    public InventaireDto soumettre(@PathVariable Long id, @CurrentUser Long userId) {
        return inventaireService.soumettre(id, userId);
    }

    @PostMapping("/{id}/reouverture")
    @PreAuthorize("hasRole('ADMIN')")
    public InventaireDto rouvrir(@PathVariable Long id) {
        return inventaireService.rouvrir(id);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize("hasRole('ADMIN')")
    public InventaireDto valider(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean nonComptesAZero,
                                 @CurrentUser Long userId) {
        return inventaireService.valider(id, nonComptesAZero, userId);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize("hasRole('ADMIN')")
    public InventaireDto annuler(@PathVariable Long id) {
        return inventaireService.annuler(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventaire-" + id + ".xlsx\"")
                .body(inventaireService.exportRapport(id));
    }
}
