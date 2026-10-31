package org.esoalahy.stock.modules.stock;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.User;
import org.esoalahy.stock.core.user.UserRepository;
import org.esoalahy.stock.modules.catalogue.CatalogueService;
import org.esoalahy.stock.modules.catalogue.dto.ProduitDto;
import org.esoalahy.stock.modules.catalogue.dto.ProduitRequest;
import org.esoalahy.stock.modules.catalogue.dto.VarianteRequest;
import org.esoalahy.stock.modules.depot.DepotService;
import org.esoalahy.stock.modules.depot.dto.DepotDto;
import org.esoalahy.stock.modules.depot.dto.DepotRequest;
import org.esoalahy.stock.modules.stock.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Règles de gestion du lot 1 sur une vraie base PostgreSQL (profil test) :
 * CUMP (RG-04), immuabilité (RG-05), jamais de négatif (RG-06), transferts
 * et contre-passation. Chaque test crée ses propres articles et dépôts.
 */
@SpringBootTest
class StockServiceTest {

    @Autowired StockService stockService;
    @Autowired StockQueryService queryService;
    @Autowired CatalogueService catalogueService;
    @Autowired DepotService depotService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    Long userId;

    @BeforeEach
    void connecterAdmin() {
        String email = "it-" + UUID.randomUUID() + "@stock.mg";
        userId = userRepository.save(User.builder().email(email).fullName("IT").role(Role.ADMIN)
                .passwordHash("x").build()).getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void le_cump_est_pondere_a_chaque_entree_et_les_sorties_se_valorisent_au_cump() {
        Long depot = depot();
        Long v = variante();
        entree(v, depot, "20", "21500");
        entree(v, depot, "10", "25000");

        StockLigneDto ligne = queryService.etat(depot, v, null, false).get(0);
        assertThat(ligne.qtePhysique()).isEqualByComparingTo("30");
        assertThat(ligne.cump()).isEqualByComparingTo("22666.6667");

        MouvementDto sortie = stockService.mouvement(new MouvementRequest(TypeMouvement.SORTIE, depot, null,
                List.of(new LigneMouvementRequest(v, new BigDecimal("5"), null, null))), userId).mouvements().get(0);
        assertThat(sortie.coutUnitaire()).isEqualByComparingTo("22666.6667");
        assertThat(sortie.valeur()).isEqualTo(113333L);
    }

    @Test
    void rg06_le_stock_physique_ne_devient_jamais_negatif() {
        Long depot = depot();
        Long v = variante();
        entree(v, depot, "3", "1000");

        assertThatThrownBy(() -> stockService.mouvement(new MouvementRequest(TypeMouvement.SORTIE, depot, null,
                List.of(new LigneMouvementRequest(v, new BigDecimal("4"), null, null))), userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Stock insuffisant");
        assertThat(queryService.etat(depot, v, null, false).get(0).qtePhysique()).isEqualByComparingTo("3");
    }

    @Test
    void rg05_un_mouvement_ne_se_modifie_ni_ne_se_supprime_en_base() {
        Long depot = depot();
        Long v = variante();
        Long id = entree(v, depot, "1", "1000");

        assertThatThrownBy(() -> jdbc.update("UPDATE mouvements_stock SET quantite = 2 WHERE id = ?", id))
                .hasMessageContaining("RG-05");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM mouvements_stock WHERE id = ?", id))
                .hasMessageContaining("RG-05");
    }

    @Test
    void un_transfert_annule_remet_les_deux_depots_et_leurs_cump_dans_leur_etat() {
        Long a = depot();
        Long b = depot();
        Long v = variante();
        entree(v, a, "10", "2000");
        entree(v, b, "4", "1000");

        DocumentMouvementDto trf = stockService.transfert(new TransfertRequest(a, b, null,
                List.of(new LigneMouvementRequest(v, new BigDecimal("3"), null, null))), userId);
        assertThat(queryService.etat(b, v, null, false).get(0).cump()).isEqualByComparingTo("1428.5714");

        stockService.annuler(trf.mouvements().get(0).id(), "erreur de dépôt", userId);
        assertThat(queryService.etat(a, v, null, false).get(0).qtePhysique()).isEqualByComparingTo("10");
        StockLigneDto lb = queryService.etat(b, v, null, false).get(0);
        assertThat(lb.qtePhysique()).isEqualByComparingTo("4");
        assertThat(lb.cump()).isEqualByComparingTo("1000");

        assertThatThrownBy(() -> stockService.annuler(trf.mouvements().get(1).id(), "encore", userId))
                .isInstanceOf(ConflictException.class);
    }

    // ── Fabriques ───────────────────────────────────────────────────────────

    private Long depot() {
        String code = "D" + UUID.randomUUID().toString().substring(0, 8);
        DepotDto d = depotService.create(new DepotRequest(code, "Dépôt " + code, null, true));
        return d.id();
    }

    private Long variante() {
        String ref = "IT-" + UUID.randomUUID().toString().substring(0, 8);
        ProduitDto p = catalogueService.create(new ProduitRequest(ref, "Article " + ref, null, null, null, null,
                null, null, null, false, null,
                List.of(new VarianteRequest(ref, null, null, 1000L, null, null, null, BigDecimal.ZERO))));
        return p.variantes().get(0).id();
    }

    private Long entree(Long v, Long depot, String qte, String cout) {
        return stockService.mouvement(new MouvementRequest(TypeMouvement.ENTREE, depot, null,
                List.of(new LigneMouvementRequest(v, new BigDecimal(qte), new BigDecimal(cout), null))), userId)
                .mouvements().get(0).id();
    }
}
