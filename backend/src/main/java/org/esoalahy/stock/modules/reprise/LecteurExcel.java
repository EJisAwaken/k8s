package org.esoalahy.stock.modules.reprise;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Lecture tolérante d'un classeur : la première ligne porte les en-têtes,
 * reconnus sans accents ni casse (« Prix détail » = « prix_detail »).
 */
final class LecteurExcel {

    /** Une ligne lue : numéro Excel (1-based, en-tête = 1) et valeurs par en-tête normalisé. */
    record Ligne(int numero, Map<String, String> valeurs) {
        String texte(String colonne) {
            String v = valeurs.get(colonne);
            return v == null || v.isBlank() ? null : v.trim();
        }

        BigDecimal nombre(String colonne) {
            String v = texte(colonne);
            if (v == null) return null;
            try {
                return new BigDecimal(v.replace(" ", "").replace(" ", "").replace(",", "."));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("« " + colonne + " » n'est pas un nombre : " + v);
            }
        }

        Long entier(String colonne) {
            BigDecimal b = nombre(colonne);
            if (b == null) return null;
            try {
                return b.setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("« " + colonne + " » doit être un montant entier en ariary : " + b);
            }
        }

        boolean booleen(String colonne) {
            String v = texte(colonne);
            if (v == null) return false;
            String n = normaliser(v);
            return n.equals("oui") || n.equals("o") || n.equals("true") || n.equals("1") || n.equals("x");
        }
    }

    private LecteurExcel() {}

    static List<Ligne> lire(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        try (InputStream in = fichier.getInputStream(); Workbook classeur = WorkbookFactory.create(in)) {
            Sheet feuille = classeur.getSheetAt(0);
            DataFormatter format = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluateur = classeur.getCreationHelper().createFormulaEvaluator();
            Row entete = feuille.getRow(feuille.getFirstRowNum());
            if (entete == null) throw new IllegalArgumentException("La première ligne doit porter les en-têtes.");
            List<String> colonnes = new ArrayList<>();
            for (int c = 0; c < entete.getLastCellNum(); c++) {
                Cell cell = entete.getCell(c);
                colonnes.add(cell == null ? "" : normaliser(format.formatCellValue(cell)));
            }
            List<Ligne> lignes = new ArrayList<>();
            for (int r = feuille.getFirstRowNum() + 1; r <= feuille.getLastRowNum(); r++) {
                Row row = feuille.getRow(r);
                if (row == null) continue;
                Map<String, String> valeurs = new HashMap<>();
                boolean vide = true;
                for (int c = 0; c < colonnes.size(); c++) {
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : valeur(cell, format, evaluateur);
                    if (!v.isBlank()) vide = false;
                    if (!colonnes.get(c).isEmpty()) valeurs.put(colonnes.get(c), v);
                }
                if (!vide) lignes.add(new Ligne(r + 1, valeurs));
            }
            return lignes;
        } catch (IOException | org.apache.poi.EmptyFileException e) {
            throw new IllegalArgumentException("Fichier illisible : envoyez un classeur Excel (.xlsx).");
        }
    }

    /** Les nombres sont relus sans format d'affichage (pas de « 1 234,00 » ni de notation scientifique). */
    private static String valeur(Cell cell, DataFormatter format, FormulaEvaluator evaluateur) {
        CellType type = cell.getCellType() == CellType.FORMULA ? evaluateur.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return format.formatCellValue(cell, evaluateur);
    }

    static String normaliser(String s) {
        String sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
