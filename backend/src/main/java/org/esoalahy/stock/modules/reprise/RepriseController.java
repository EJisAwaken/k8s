package org.esoalahy.stock.modules.reprise;

import org.esoalahy.stock.core.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Reprise de données : réservée à l'administrateur. */
@RestController
@RequestMapping("/api/v1/reprise")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RepriseController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RepriseService repriseService;

    @PostMapping(value = "/catalogue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RapportImportDto catalogue(@RequestPart("file") MultipartFile file,
                                      @RequestParam(defaultValue = "false") boolean simulation) {
        return repriseService.importerCatalogue(file, simulation);
    }

    @PostMapping(value = "/stock-initial", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RapportImportDto stockInitial(@RequestPart("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean simulation,
                                         @CurrentUser Long userId) {
        return repriseService.importerStockInitial(file, simulation, userId);
    }

    @GetMapping("/modeles/catalogue")
    public ResponseEntity<byte[]> modeleCatalogue() {
        return fichier(repriseService.modeleCatalogue(), "modele-catalogue.xlsx");
    }

    @GetMapping("/modeles/stock-initial")
    public ResponseEntity<byte[]> modeleStock() {
        return fichier(repriseService.modeleStock(), "modele-stock-initial.xlsx");
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .body(contenu);
    }
}
