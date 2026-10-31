package org.esoalahy.stock.modules.catalogue;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.ForbiddenException;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.pagination.PageDto;
import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.RoleRules;
import org.esoalahy.stock.modules.catalogue.dto.*;
import org.esoalahy.stock.modules.stock.MouvementStockRepository;
import org.esoalahy.stock.modules.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Catalogue produits (cahier §3.1).
 *
 * <ul>
 *   <li>RG-01 : un produit (ou une variante) qui a des mouvements ne se
 *       supprime pas, il s'archive.</li>
 *   <li>Le responsable achats ne modifie pas les prix de vente (§2).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CatalogueService {

    private final CategorieRepository categorieRepository;
    private final ProduitRepository produitRepository;
    private final VarianteRepository varianteRepository;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;

    // ── Catégories ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategorieDto> categories() {
        return categorieRepository.findAllByOrderByNomAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public CategorieDto createCategorie(CategorieRequest req) {
        String nom = req.nom().trim();
        categorieRepository.findByNomIgnoreCase(nom).ifPresent(c -> {
            throw new ConflictException("La catégorie « " + nom + " » existe déjà.");
        });
        Categorie c = new Categorie();
        appliquer(c, req, nom);
        return toDto(categorieRepository.save(c));
    }

    @Transactional
    public CategorieDto updateCategorie(Long id, CategorieRequest req) {
        Categorie c = categorieRepository.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
        String nom = req.nom().trim();
        categorieRepository.findByNomIgnoreCase(nom).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new ConflictException("La catégorie « " + nom + " » existe déjà.");
        });
        if (id.equals(req.parentId())) {
            throw new IllegalArgumentException("Une catégorie ne peut pas être sa propre parente.");
        }
        appliquer(c, req, nom);
        return toDto(c);
    }

    @Transactional
    public void deleteCategorie(Long id) {
        if (produitRepository.countByCategorieId(id) > 0 || categorieRepository.countByParentId(id) > 0) {
            throw new ConflictException("Cette catégorie est utilisée : désactivez-la plutôt que de la supprimer.");
        }
        categorieRepository.deleteById(id);
    }

    private void appliquer(Categorie c, CategorieRequest req, String nom) {
        c.setNom(nom);
        c.setParent(req.parentId() == null ? null : categorieRepository.findById(req.parentId())
                .orElseThrow(() -> new NotFoundException("Catégorie parente introuvable.")));
        if (req.actif() != null) c.setActif(req.actif());
    }

    private CategorieDto toDto(Categorie c) {
        return new CategorieDto(c.getId(), c.getNom(),
                c.getParent() == null ? null : c.getParent().getId(),
                c.getParent() == null ? null : c.getParent().getNom(),
                c.isActif(), produitRepository.countByCategorieId(c.getId()));
    }

    // ── Produits ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageDto<ProduitDto> search(String q, Long categorieId, Boolean archive, int page, int size) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        String exact = q == null ? "" : q.trim();
        Page<Produit> p = produitRepository.search(like, exact, categorieId, archive,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("nom")));
        return PageDto.of(p.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ProduitDto get(Long id) {
        return toDto(require(id));
    }

    @Transactional(readOnly = true)
    public List<VarianteResumeDto> rechercherVariantes(String q, int limite) {
        if (q == null || q.isBlank()) return List.of();
        return varianteRepository.rechercher("%" + q.trim().toLowerCase() + "%", q.trim(),
                        PageRequest.of(0, Math.min(Math.max(limite, 1), 50)))
                .stream().map(VarianteResumeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VarianteResumeDto> variantesParIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return varianteRepository.findAllWithProduit(ids.stream().limit(200).toList())
                .stream().map(VarianteResumeDto::from).toList();
    }

    /** Scan d'une douchette : code-barres exact, puis SKU. */
    @Transactional(readOnly = true)
    public VarianteResumeDto scanner(String code) {
        String c = code == null ? "" : code.trim();
        return varianteRepository.findByCodeBarres(c)
                .or(() -> varianteRepository.findBySkuIgnoreCase(c))
                .map(VarianteResumeDto::from)
                .orElseThrow(() -> new NotFoundException("Aucun article ne correspond au code « " + c + " »."));
    }

    @Transactional
    public ProduitDto create(ProduitRequest req) {
        String reference = req.reference().trim();
        produitRepository.findByReferenceIgnoreCase(reference).ifPresent(p -> {
            throw new ConflictException("La référence « " + reference + " » existe déjà.");
        });
        Produit p = new Produit();
        appliquer(p, req, reference);
        produitRepository.save(p);

        List<VarianteRequest> variantes = req.variantes() == null || req.variantes().isEmpty()
                ? List.of(new VarianteRequest(reference, null, null, null, null, null, null, BigDecimal.ZERO))
                : req.variantes();
        for (VarianteRequest vr : variantes) {
            ajouterVariante(p, vr);
        }
        return toDto(p);
    }

    @Transactional
    public ProduitDto update(Long id, ProduitRequest req) {
        Produit p = require(id);
        String reference = req.reference().trim();
        produitRepository.findByReferenceIgnoreCase(reference).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new ConflictException("La référence « " + reference + " » existe déjà.");
        });
        boolean suiviSerie = Boolean.TRUE.equals(req.suiviSerie());
        if (suiviSerie != p.isSuiviSerie() && mouvementRepository.existsByProduitId(id)) {
            throw new ConflictException("Le suivi par numéro de série ne peut plus changer : ce produit a déjà des mouvements.");
        }
        appliquer(p, req, reference);
        return toDto(p);
    }

    /** RG-01 : suppression seulement sans mouvement ; sinon archivage. */
    @Transactional
    public void delete(Long id) {
        Produit p = require(id);
        if (mouvementRepository.existsByProduitId(id)) {
            throw new ConflictException("RG-01 : ce produit a des mouvements de stock, il ne peut pas être supprimé. "
                    + "Archivez-le.");
        }
        produitRepository.delete(p);
    }

    @Transactional
    public ProduitDto archiver(Long id, boolean archive) {
        Produit p = require(id);
        p.setArchive(archive);
        return toDto(p);
    }

    // ── Variantes ───────────────────────────────────────────────────────────

    @Transactional
    public ProduitDto addVariante(Long produitId, VarianteRequest req) {
        Produit p = require(produitId);
        ajouterVariante(p, req);
        return toDto(p);
    }

    @Transactional
    public ProduitDto updateVariante(Long varianteId, VarianteRequest req) {
        Variante v = requireVariante(varianteId);
        controlerPrix(v, req);
        String sku = req.sku().trim();
        varianteRepository.findBySkuIgnoreCase(sku).filter(o -> !o.getId().equals(varianteId)).ifPresent(o -> {
            throw new ConflictException("Le SKU « " + sku + " » existe déjà.");
        });
        appliquer(v, req, sku);
        if (v.getCodeBarres() == null) {
            v.setCodeBarres(CodesBarres.eanInterne(v.getId()));
        }
        return toDto(v.getProduit());
    }

    @Transactional
    public ProduitDto deleteVariante(Long varianteId) {
        Variante v = requireVariante(varianteId);
        Produit p = v.getProduit();
        if (mouvementRepository.existsByVarianteId(varianteId)) {
            throw new ConflictException("RG-01 : cette variante a des mouvements de stock, archivez-la.");
        }
        if (p.getVariantes().size() <= 1) {
            throw new ConflictException("Un produit garde au moins une variante.");
        }
        p.getVariantes().remove(v);
        varianteRepository.delete(v);
        return toDto(p);
    }

    @Transactional
    public ProduitDto archiverVariante(Long varianteId, boolean archive) {
        Variante v = requireVariante(varianteId);
        v.setArchive(archive);
        return toDto(v.getProduit());
    }

    private void ajouterVariante(Produit p, VarianteRequest req) {
        controlerPrix(null, req);
        String sku = req.sku().trim();
        varianteRepository.findBySkuIgnoreCase(sku).ifPresent(o -> {
            throw new ConflictException("Le SKU « " + sku + " » existe déjà.");
        });
        Variante v = new Variante();
        v.setProduit(p);
        appliquer(v, req, sku);
        varianteRepository.saveAndFlush(v);
        if (v.getCodeBarres() == null) {
            v.setCodeBarres(CodesBarres.eanInterne(v.getId()));
        }
        p.getVariantes().add(v);
    }

    private void appliquer(Variante v, VarianteRequest req, String sku) {
        String code = blankToNull(req.codeBarres());
        if (code != null && !code.equals(v.getCodeBarres())) {
            varianteRepository.findByCodeBarres(code).filter(o -> !o.getId().equals(v.getId())).ifPresent(o -> {
                throw new ConflictException("Le code-barres « " + code + " » est déjà attribué à « " + o.libelle() + " ».");
            });
        }
        v.setSku(sku);
        v.setAttributs(blankToNull(req.attributs()));
        v.setCodeBarres(code != null ? code : v.getCodeBarres());
        v.setPrixDetail(req.prixDetail());
        v.setPrixGros(req.prixGros());
        v.setPrixRevendeur(req.prixRevendeur());
        v.setSeuilGros(req.seuilGros());
        v.setSeuilMini(req.seuilMini() == null ? BigDecimal.ZERO : req.seuilMini());
    }

    /** Le responsable achats ne fixe ni ne modifie les prix de vente. */
    private static void controlerPrix(Variante actuelle, VarianteRequest req) {
        if (RoleRules.currentRole() != Role.ACHATS) return;
        boolean change = actuelle == null
                ? req.prixDetail() != null || req.prixGros() != null || req.prixRevendeur() != null
                : !Objects.equals(actuelle.getPrixDetail(), req.prixDetail())
                        || !Objects.equals(actuelle.getPrixGros(), req.prixGros())
                        || !Objects.equals(actuelle.getPrixRevendeur(), req.prixRevendeur());
        if (change) {
            throw new ForbiddenException("Votre profil ne peut pas fixer les prix de vente.");
        }
    }

    private void appliquer(Produit p, ProduitRequest req, String reference) {
        p.setReference(reference);
        p.setNom(req.nom().trim());
        p.setNomFournisseur(blankToNull(req.nomFournisseur()));
        p.setReferenceFournisseur(blankToNull(req.referenceFournisseur()));
        p.setCategorie(req.categorieId() == null ? null : categorieRepository.findById(req.categorieId())
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable.")));
        p.setUnite(req.unite() == null || req.unite().isBlank() ? "pièce" : req.unite().trim());
        p.setPoidsKg(req.poidsKg());
        p.setVolumeM3(req.volumeM3());
        p.setCodeSh(blankToNull(req.codeSh()));
        p.setSuiviSerie(Boolean.TRUE.equals(req.suiviSerie()));
        p.setDescription(blankToNull(req.description()));
    }

    private ProduitDto toDto(Produit p) {
        List<Long> ids = p.getVariantes().stream().map(Variante::getId).filter(Objects::nonNull).toList();
        Map<Long, BigDecimal[]> totaux = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : stockRepository.totauxParVariante(ids)) {
                totaux.put((Long) row[0], new BigDecimal[]{(BigDecimal) row[1], (BigDecimal) row[2]});
            }
        }
        List<VarianteDto> variantes = p.getVariantes().stream().map(v -> {
            BigDecimal[] t = totaux.getOrDefault(v.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            return new VarianteDto(v.getId(), p.getId(), v.getSku(), v.getAttributs(), v.libelle(), v.getCodeBarres(),
                    v.getPrixDetail(), v.getPrixGros(), v.getPrixRevendeur(), v.getSeuilGros(), v.getSeuilMini(),
                    v.isArchive(), t[0], t[0].subtract(t[1]));
        }).toList();
        return new ProduitDto(p.getId(), p.getReference(), p.getNom(), p.getNomFournisseur(),
                p.getReferenceFournisseur(),
                p.getCategorie() == null ? null : p.getCategorie().getId(),
                p.getCategorie() == null ? null : p.getCategorie().getNom(),
                p.getUnite(), p.getPoidsKg(), p.getVolumeM3(), p.getCodeSh(), p.isSuiviSerie(),
                p.getDescription(), p.isArchive(),
                p.getId() != null && mouvementRepository.existsByProduitId(p.getId()),
                variantes);
    }

    private Produit require(Long id) {
        return produitRepository.findById(id).orElseThrow(() -> new NotFoundException("Produit introuvable."));
    }

    private Variante requireVariante(Long id) {
        return varianteRepository.findById(id).orElseThrow(() -> new NotFoundException("Variante introuvable."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
