package org.esoalahy.stock.modules.etiquette;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.money.MgaFormat;
import org.esoalahy.stock.core.qr.QrCodes;
import org.esoalahy.stock.modules.catalogue.Variante;
import org.esoalahy.stock.modules.catalogue.VarianteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Étiquettes produits imprimables (cahier §3.1) : nom, attributs, SKU,
 * code-barres Code 128 (lisible par la douchette) et, au choix, prix détail.
 */
@Service
@RequiredArgsConstructor
public class EtiquetteService {

    private static final float MM = 72f / 25.4f;
    /** Marges de la planche A4 : 3 × 70 mm = 210 mm, 8 × 37 mm = 296 mm. */
    private static final int COLONNES_A4 = 3;
    private static final int RANGEES_A4 = 8;

    private final VarianteRepository varianteRepository;

    @Transactional(readOnly = true)
    public byte[] pdf(EtiquetteRequest req) {
        List<Long> ids = req.lignes().stream().map(EtiquetteRequest.Ligne::varianteId).distinct().toList();
        Map<Long, Variante> parId = varianteRepository.findAllWithProduit(ids).stream()
                .collect(Collectors.toMap(Variante::getId, Function.identity()));
        List<Variante> aImprimer = new ArrayList<>();
        for (EtiquetteRequest.Ligne l : req.lignes()) {
            Variante v = parId.get(l.varianteId());
            if (v == null) throw new NotFoundException("Article introuvable (id " + l.varianteId() + ").");
            for (int i = 0; i < l.copies(); i++) aImprimer.add(v);
        }
        if (aImprimer.size() > 5000) {
            throw new IllegalArgumentException("5 000 étiquettes au plus par impression.");
        }
        try {
            return req.format() == FormatEtiquette.A4_3X8
                    ? planche(aImprimer, req.afficherPrix())
                    : rouleau(aImprimer, req.afficherPrix());
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Impossible de produire les étiquettes.", e);
        }
    }

    private byte[] planche(List<Variante> variantes, boolean prix) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();
        float l = FormatEtiquette.A4_3X8.largeurMm * MM;
        float h = FormatEtiquette.A4_3X8.hauteurMm * MM;
        float hautPage = PageSize.A4.getHeight();
        int parPage = COLONNES_A4 * RANGEES_A4;
        for (int i = 0; i < variantes.size(); i++) {
            int pos = i % parPage;
            if (i > 0 && pos == 0) doc.newPage();
            float x = (pos % COLONNES_A4) * l;
            float y = hautPage - (pos / COLONNES_A4 + 1) * h;
            dessiner(writer.getDirectContent(), variantes.get(i), x, y, l, h, prix);
        }
        doc.close();
        return out.toByteArray();
    }

    private byte[] rouleau(List<Variante> variantes, boolean prix) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        float l = FormatEtiquette.ROULEAU_50X30.largeurMm * MM;
        float h = FormatEtiquette.ROULEAU_50X30.hauteurMm * MM;
        Document doc = new Document(new Rectangle(l, h), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();
        for (int i = 0; i < variantes.size(); i++) {
            if (i > 0) doc.newPage();
            dessiner(writer.getDirectContent(), variantes.get(i), 0, 0, l, h, prix);
        }
        doc.close();
        return out.toByteArray();
    }

    private void dessiner(PdfContentByte cb, Variante v, float x, float y, float l, float h, boolean prix)
            throws DocumentException, IOException {
        BaseFont gras = police(BaseFont.HELVETICA_BOLD);
        BaseFont normal = police(BaseFont.HELVETICA);
        float marge = 2.5f * MM;
        float largeurUtile = l - 2 * marge;

        // Nom (une ligne, tronquée) puis attributs.
        float yTexte = y + h - marge - 7;
        cb.beginText();
        cb.setFontAndSize(gras, 7.5f);
        cb.setTextMatrix(x + marge, yTexte);
        cb.showText(tronquer(v.getProduit().getNom(), gras, 7.5f, largeurUtile));
        if (v.getAttributs() != null) {
            cb.setFontAndSize(normal, 6.5f);
            cb.setTextMatrix(x + marge, yTexte - 8);
            cb.showText(tronquer(v.getAttributs(), normal, 6.5f, largeurUtile));
        }
        cb.endText();

        // Code-barres au centre.
        String code = v.getCodeBarres() != null ? v.getCodeBarres() : v.getSku();
        Image barres = Image.getInstance(QrCodes.code128(code, 600, 120));
        float hauteurBarres = h * 0.38f;
        barres.scaleAbsolute(largeurUtile, hauteurBarres);
        float yBarres = y + marge + 9;
        barres.setAbsolutePosition(x + marge, yBarres);
        cb.addImage(barres);

        // Code lisible + SKU à gauche, prix à droite.
        cb.beginText();
        cb.setFontAndSize(normal, 6f);
        cb.setTextMatrix(x + marge, y + marge + 2);
        cb.showText(tronquer(code + "  ·  " + v.getSku(), normal, 6f, prix ? largeurUtile * 0.6f : largeurUtile));
        if (prix && v.getPrixDetail() != null) {
            String texte = MgaFormat.format(v.getPrixDetail());
            cb.setFontAndSize(gras, 8.5f);
            cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, texte, x + l - marge, y + marge + 1.5f, 0);
        }
        cb.endText();
    }

    /** Polices standard PDF (non embarquées) : BaseFont les met en cache lui-même. */
    private static BaseFont police(String nom) throws DocumentException, IOException {
        return BaseFont.createFont(nom, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private static String tronquer(String texte, BaseFont police, float taille, float largeur) {
        if (police.getWidthPoint(texte, taille) <= largeur) return texte;
        String t = texte;
        while (t.length() > 1 && police.getWidthPoint(t + "…", taille) > largeur) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}
