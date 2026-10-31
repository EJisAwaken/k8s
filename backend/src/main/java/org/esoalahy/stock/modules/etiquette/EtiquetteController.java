package org.esoalahy.stock.modules.etiquette;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/etiquettes")
@RequiredArgsConstructor
public class EtiquetteController {

    private final EtiquetteService etiquetteService;

    /** POST et non GET : la liste d'articles et de copies peut être longue. Lecture seule côté données. */
    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MAGASINIER','ACHATS','COMMERCIAL')")
    public ResponseEntity<byte[]> pdf(@Valid @RequestBody EtiquetteRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"etiquettes.pdf\"")
                .body(etiquetteService.pdf(request));
    }
}
