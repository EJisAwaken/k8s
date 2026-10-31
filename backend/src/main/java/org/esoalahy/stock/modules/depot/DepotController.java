package org.esoalahy.stock.modules.depot;

import org.esoalahy.stock.modules.depot.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dépôts et emplacements : lecture pour tous, paramétrage par l'administrateur. */
@RestController
@RequestMapping("/api/v1/depots")
@RequiredArgsConstructor
public class DepotController {

    private final DepotService depotService;

    @GetMapping
    public List<DepotDto> list() {
        return depotService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DepotDto create(@Valid @RequestBody DepotRequest request) {
        return depotService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepotDto update(@PathVariable Long id, @Valid @RequestBody DepotRequest request) {
        return depotService.update(id, request);
    }

    @GetMapping("/{id}/emplacements")
    public List<EmplacementDto> emplacements(@PathVariable Long id) {
        return depotService.emplacements(id);
    }

    @PostMapping("/{id}/emplacements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER')")
    public EmplacementDto createEmplacement(@PathVariable Long id, @Valid @RequestBody EmplacementRequest request) {
        return depotService.createEmplacement(id, request);
    }

    @PutMapping("/{id}/emplacements/{emplacementId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER')")
    public EmplacementDto updateEmplacement(@PathVariable Long id, @PathVariable Long emplacementId,
                                            @Valid @RequestBody EmplacementRequest request) {
        return depotService.updateEmplacement(id, emplacementId, request);
    }
}
