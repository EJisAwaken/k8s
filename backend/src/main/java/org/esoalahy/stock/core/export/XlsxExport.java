package org.esoalahy.stock.core.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * fabrique GÉNÉRIQUE de classeurs XLSX — premier usage de poi-ooxml
 * (comme OpenPDF l'a été pour la facture. Une feuille, une ligne
 * d'en-têtes FRANÇAIS en gras figée au défilement (freeze pane), des colonnes
 * TYPÉES ({@link TypeColonne}) auto-dimensionnées.
 *
 * <p><b>Règle d'or</b> : un montant est une cellule NUMÉRIQUE au format
 * « # ##0 », JAMAIS un texte — le comptable doit pouvoir SOMMER une colonne
 * d'ariary dans Excel sans retraitement. Dates {@code dd/mm/yyyy}, instants
 * {@code dd/mm/yyyy hh:mm} (zone système — Indian/Antananarivo via
 * {@code LocaleConfig}), booléens rendus « Oui »/« Non », {@code null} →
 * cellule vide.</p>
 *
 * <p>{@link XSSFWorkbook} (tout en mémoire) suffit aux volumes d'un
 * stock courant (quelques milliers de lignes). Le jour où un export dépasse
 * la dizaine de milliers de lignes, basculer sur {@code SXSSFWorkbook}
 * (streaming, même API) — prévoir alors
 * {@code sheet.trackAllColumnsForAutoSizing()} pour conserver l'auto-size.</p>
 */
public final class XlsxExport {

    /** Type d'une colonne — porte le type de cellule et le format d'affichage. */
    public enum TypeColonne {
        /** Texte libre. */
        TEXTE,
        /** Nombre SANS format (compteurs, identifiants, taux de change). */
        ENTIER,
        /** Montant ariary : cellule NUMÉRIQUE format « # ##0 » — sommable. */
        MONTANT_AR,
        /** Pourcentage DÉJÀ exprimé en points (66.7 → « 66,7 % ») — jamais re-multiplié. */
        POURCENT,
        /** Jour local ({@link LocalDate}) — format {@code dd/mm/yyyy}. */
        DATE,
        /** Instant ({@link Instant} ou {@link LocalDateTime}) — {@code dd/mm/yyyy hh:mm}. */
        DATE_HEURE,
        /** Booléen rendu « Oui »/« Non ». */
        BOOLEEN
    }

    /** Une colonne du classeur : son en-tête FRANÇAIS et son type. */
    public record Colonne(String titre, TypeColonne type) {}

    private XlsxExport() {}

    /**
     * Construit le classeur : UNE feuille au nom donné, la ligne d'en-têtes en
     * gras figée, puis les lignes de données — chaque valeur écrite selon le
     * TYPE de sa colonne, {@code null} → cellule vide. Chaque ligne doit avoir
     * exactement une valeur par colonne (erreur de programmation sinon).
     */
    public static byte[] classeur(String nomFeuille, List<Colonne> colonnes,
                                  List<List<Object>> lignes) {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet(WorkbookUtil.createSafeSheetName(nomFeuille));
            Map<TypeColonne, CellStyle> styles = styles(classeur);

            // En-têtes français en GRAS, figés au défilement.
            Font gras = classeur.createFont();
            gras.setBold(true);
            CellStyle enTete = classeur.createCellStyle();
            enTete.setFont(gras);
            Row ligneEnTete = feuille.createRow(0);
            for (int c = 0; c < colonnes.size(); c++) {
                Cell cellule = ligneEnTete.createCell(c);
                cellule.setCellValue(colonnes.get(c).titre());
                cellule.setCellStyle(enTete);
            }
            feuille.createFreezePane(0, 1);

            int r = 1;
            for (List<Object> donnees : lignes) {
                if (donnees.size() != colonnes.size()) {
                    throw new IllegalArgumentException("Ligne " + r + " du classeur « "
                            + nomFeuille + " » : " + donnees.size() + " valeur(s) pour "
                            + colonnes.size() + " colonne(s).");
                }
                Row ligne = feuille.createRow(r++);
                for (int c = 0; c < colonnes.size(); c++) {
                    ecrire(ligne, c, colonnes.get(c).type(), donnees.get(c), styles);
                }
            }

            for (int c = 0; c < colonnes.size(); c++) {
                feuille.autoSizeColumn(c);
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la construction du classeur XLSX « "
                    + nomFeuille + " ».", e);
        }
    }

    /** UN style par type à format (limite Excel : 64 000 styles par classeur). */
    private static Map<TypeColonne, CellStyle> styles(Workbook classeur) {
        DataFormat formats = classeur.createDataFormat();
        Map<TypeColonne, CellStyle> styles = new EnumMap<>(TypeColonne.class);
        styles.put(TypeColonne.MONTANT_AR, style(classeur, formats, "# ##0"));
        // Le « % » est CITÉ : un % nu multiplierait la valeur par 100 dans Excel.
        styles.put(TypeColonne.POURCENT, style(classeur, formats, "0.0\" %\""));
        styles.put(TypeColonne.DATE, style(classeur, formats, "dd/mm/yyyy"));
        styles.put(TypeColonne.DATE_HEURE, style(classeur, formats, "dd/mm/yyyy hh:mm"));
        return styles;
    }

    private static CellStyle style(Workbook classeur, DataFormat formats, String format) {
        CellStyle style = classeur.createCellStyle();
        style.setDataFormat(formats.getFormat(format));
        return style;
    }

    /** Écrit UNE valeur selon le type de sa colonne — {@code null} : cellule vide. */
    private static void ecrire(Row ligne, int c, TypeColonne type, Object valeur,
                               Map<TypeColonne, CellStyle> styles) {
        if (valeur == null) {
            return; // cellule absente = cellule vide
        }
        Cell cellule = ligne.createCell(c);
        CellStyle style = styles.get(type);
        if (style != null) {
            cellule.setCellStyle(style);
        }
        switch (type) {
            case TEXTE -> cellule.setCellValue(valeur.toString());
            case ENTIER, MONTANT_AR, POURCENT -> cellule.setCellValue(nombre(type, valeur));
            case DATE -> cellule.setCellValue(dateLocale(valeur));
            case DATE_HEURE -> cellule.setCellValue(dateHeureLocale(valeur));
            case BOOLEEN -> cellule.setCellValue(booleen(valeur) ? "Oui" : "Non");
        }
    }

    private static double nombre(TypeColonne type, Object valeur) {
        if (valeur instanceof Number nombre) {
            return nombre.doubleValue();
        }
        throw new IllegalArgumentException("Colonne " + type + " : valeur non numérique ("
                + valeur.getClass().getSimpleName() + ").");
    }

    private static LocalDate dateLocale(Object valeur) {
        if (valeur instanceof LocalDate date) {
            return date;
        }
        throw new IllegalArgumentException("Colonne DATE : LocalDate attendu ("
                + valeur.getClass().getSimpleName() + ").");
    }

    private static LocalDateTime dateHeureLocale(Object valeur) {
        if (valeur instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (valeur instanceof LocalDateTime dateHeure) {
            return dateHeure;
        }
        throw new IllegalArgumentException("Colonne DATE_HEURE : Instant ou LocalDateTime "
                + "attendu (" + valeur.getClass().getSimpleName() + ").");
    }

    private static boolean booleen(Object valeur) {
        if (valeur instanceof Boolean booleen) {
            return booleen;
        }
        throw new IllegalArgumentException("Colonne BOOLEEN : Boolean attendu ("
                + valeur.getClass().getSimpleName() + ").");
    }
}
