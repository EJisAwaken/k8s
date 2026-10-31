package org.esoalahy.stock.modules.catalogue;

import org.esoalahy.stock.core.pagination.PageDto;
import org.esoalahy.stock.modules.catalogue.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalogue : lecture pour tous les profils ; création et modification par
 * l'administrateur et le responsable achats (sans les prix de vente pour ce
 * dernier — contrôlé dans le service).
 */
@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
public class CatalogueController {

    private static final String ECRITURE = "hasAnyRole('ADMIN','ACHATS')";

    private final CatalogueService catalogueService;

    // ── Catégories
    @GetMapping("/categories")
    public List<CategorieDto> categories() {
        return catalogueService.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ECRITURE)
    public CategorieDto createCategorie(@Valid @RequestBody CategorieRequest request) {
        return catalogueService.createCategorie(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(ECRITURE)
    public CategorieDto updateCategorie(@PathVariable Long id, @Valid @RequestBody CategorieRequest request) {
        return catalogueService.updateCategorie(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ECRITURE)
    public void deleteCategorie(@PathVariable Long id) {
        catalogueService.deleteCategorie(id);
    }

    // ── Produits
    @GetMapping("/produits")
    public PageDto<ProduitDto> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Long categorieId,
                                      @RequestParam(required = false, defaultValue = "false") Boolean archive,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        return catalogueService.search(q, categorieId, archive, page, size);
    }

    @GetMapping("/produits/{id}")
    public ProduitDto get(@PathVariable Long id) {
        return catalogueService.get(id);
    }

    @PostMapping("/produits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ECRITURE)
    public ProduitDto create(@Valid @RequestBody ProduitRequest request) {
        return catalogueService.create(request);
    }

    @PutMapping("/produits/{id}")
    @PreAuthorize(ECRITURE)
    public ProduitDto update(@PathVariable Long id, @Valid @RequestBody ProduitRequest request) {
        return catalogueService.update(id, request);
    }

    @DeleteMapping("/produits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        catalogueService.delete(id);
    }

    @PostMapping("/produits/{id}/archive")
    @PreAuthorize(ECRITURE)
    public ProduitDto archiver(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean archive) {
        return catalogueService.archiver(id, archive);
    }

    // ── Variantes
    @GetMapping("/variantes/recherche")
    public List<VarianteResumeDto> rechercher(@RequestParam String q, @RequestParam(defaultValue = "20") int limite) {
        return catalogueService.rechercherVariantes(q, limite);
    }

    @GetMapping("/variantes")
    public List<VarianteResumeDto> parIds(@RequestParam List<Long> ids) {
        return catalogueService.variantesParIds(ids);
    }

    @GetMapping("/variantes/scan/{code}")
    public VarianteResumeDto scanner(@PathVariable String code) {
        return catalogueService.scanner(code);
    }

    @PostMapping("/produits/{id}/variantes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ECRITURE)
    public ProduitDto addVariante(@PathVariable Long id, @Valid @RequestBody VarianteRequest request) {
        return catalogueService.addVariante(id, request);
    }

    @PutMapping("/variantes/{id}")
    @PreAuthorize(ECRITURE)
    public ProduitDto updateVariante(@PathVariable Long id, @Valid @RequestBody VarianteRequest request) {
        return catalogueService.updateVariante(id, request);
    }

    @DeleteMapping("/variantes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProduitDto deleteVariante(@PathVariable Long id) {
        return catalogueService.deleteVariante(id);
    }

    @PostMapping("/variantes/{id}/archive")
    @PreAuthorize(ECRITURE)
    public ProduitDto archiverVariante(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean archive) {
        return catalogueService.archiverVariante(id, archive);
    }
}
