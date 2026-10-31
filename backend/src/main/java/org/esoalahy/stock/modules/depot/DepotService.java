package org.esoalahy.stock.modules.depot;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.modules.depot.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepotService {

    private final DepotRepository depotRepository;
    private final EmplacementRepository emplacementRepository;

    @Transactional(readOnly = true)
    public List<DepotDto> list() {
        return depotRepository.findAllByOrderByNomAsc().stream().map(DepotDto::from).toList();
    }

    @Transactional
    public DepotDto create(DepotRequest req) {
        String code = req.code().trim().toUpperCase();
        depotRepository.findByCodeIgnoreCase(code).ifPresent(d -> {
            throw new ConflictException("Le code dépôt « " + code + " » est déjà utilisé.");
        });
        Depot d = new Depot();
        appliquer(d, req, code);
        return DepotDto.from(depotRepository.save(d));
    }

    @Transactional
    public DepotDto update(Long id, DepotRequest req) {
        Depot d = require(id);
        String code = req.code().trim().toUpperCase();
        depotRepository.findByCodeIgnoreCase(code)
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new ConflictException("Le code dépôt « " + code + " » est déjà utilisé.");
                });
        appliquer(d, req, code);
        return DepotDto.from(d);
    }

    @Transactional(readOnly = true)
    public List<EmplacementDto> emplacements(Long depotId) {
        require(depotId);
        return emplacementRepository.findByDepotIdOrderByCodeAsc(depotId).stream().map(EmplacementDto::from).toList();
    }

    @Transactional
    public EmplacementDto createEmplacement(Long depotId, EmplacementRequest req) {
        Depot depot = require(depotId);
        String code = req.code().trim().toUpperCase();
        emplacementRepository.findByDepotAndCode(depotId, code).ifPresent(e -> {
            throw new ConflictException("L'emplacement « " + code + " » existe déjà dans ce dépôt.");
        });
        Emplacement e = new Emplacement();
        e.setDepot(depot);
        e.setCode(code);
        e.setLibelle(blankToNull(req.libelle()));
        e.setActif(req.actif() == null || req.actif());
        return EmplacementDto.from(emplacementRepository.save(e));
    }

    @Transactional
    public EmplacementDto updateEmplacement(Long depotId, Long id, EmplacementRequest req) {
        Emplacement e = emplacementRepository.findById(id)
                .filter(x -> x.getDepot().getId().equals(depotId))
                .orElseThrow(() -> new NotFoundException("Emplacement introuvable."));
        String code = req.code().trim().toUpperCase();
        emplacementRepository.findByDepotAndCode(depotId, code)
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new ConflictException("L'emplacement « " + code + " » existe déjà dans ce dépôt.");
                });
        e.setCode(code);
        e.setLibelle(blankToNull(req.libelle()));
        if (req.actif() != null) e.setActif(req.actif());
        return EmplacementDto.from(e);
    }

    public Depot require(Long id) {
        return depotRepository.findById(id).orElseThrow(() -> new NotFoundException("Dépôt introuvable."));
    }

    /** Dépôt utilisable pour un mouvement : existe et actif. */
    public Depot requireActif(Long id) {
        Depot d = require(id);
        if (!d.isActif()) {
            throw new ConflictException("Le dépôt « " + d.getNom() + " » est désactivé.");
        }
        return d;
    }

    private static void appliquer(Depot d, DepotRequest req, String code) {
        d.setCode(code);
        d.setNom(req.nom().trim());
        d.setAdresse(blankToNull(req.adresse()));
        if (req.actif() != null) d.setActif(req.actif());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
