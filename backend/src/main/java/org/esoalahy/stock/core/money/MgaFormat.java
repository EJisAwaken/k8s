package org.esoalahy.stock.core.money;

import java.util.Locale;

/**
 * Formatage/parsing des montants Ariary — la devise de tenue est le
 * MGA, en montants ENTIERS (les décimales d'ariary n'existent pas en pratique,
 *, affichés avec séparateur de milliers à la française :
 * « 1 234 500 Ar ».
 *
 * <p>Le séparateur est l'espace ORDINAIRE (U+0020), pas l'insécable : les
 * montants formatés partent aussi en SMS et un caractère hors GSM-7
 * ferait basculer tout le message en UCS-2 (SMS deux fois plus cher). Le
 * parsing, lui, tolère tous les types d'espaces (insécable, fine…) et le
 * suffixe « Ar » / « MGA » / « ariary » : on relit ce que l'on écrit, et ce
 * qu'un utilisateur colle depuis un document.</p>
 *
 * <p>Utilitaire pur, sans état ni dépendance Spring — utilisable des futurs
 * PDF de factures comme des templates SMS.</p>
 */
public final class MgaFormat {

    /** Suffixe monétaire affiché (usage malgache courant : « Ar »). */
    public static final String SUFFIXE = "Ar";

    private MgaFormat() {}

    // ── Formatage ────────────────────────────────────────────────────────────

    /** « 1 234 500 Ar » — montant entier avec suffixe monétaire. */
    public static String format(long montant) {
        return formatNombre(montant) + " " + SUFFIXE;
    }

    /** « 1 234 500 » — le nombre seul, milliers séparés par une espace. */
    public static String formatNombre(long montant) {
        String digits = Long.toString(Math.abs(montant));
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return (montant < 0 ? "-" : "") + sb;
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /**
     * Relit un montant Ariary saisi ou formaté : « 1 234 500 », « 1234500 »,
     * « 1 234 500 Ar », « 2 000 000 MGA », « -500 ariary »… Tolère espaces
     * ordinaires, insécables (U+00A0) et fines (U+202F, U+2009).
     *
     * @throws IllegalArgumentException message en français si le texte n'est
     *                                  pas un montant entier lisible
     */
    public static long parse(String texte) {
        if (texte == null || texte.isBlank()) {
            throw new IllegalArgumentException("Le montant est vide.");
        }
        String s = texte.trim();
        // Suffixes monétaires acceptés, insensibles à la casse.
        String lower = s.toLowerCase(Locale.ROOT);
        for (String suffixe : new String[]{"ariary", "mga", "ar"}) {
            if (lower.endsWith(suffixe)) {
                s = s.substring(0, s.length() - suffixe.length());
                break;
            }
        }
        // Toutes les variantes d'espaces disparaissent (séparateurs de milliers).
        s = s.replaceAll("[\\s   ]", "");
        if (!s.matches("-?\\d+")) {
            throw new IllegalArgumentException(
                    "Montant illisible : « " + texte.trim() + " ». "
                            + "Saisissez un montant entier en ariary (ex. « 1 234 500 »).");
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Montant trop grand : « " + texte.trim() + " ».");
        }
    }
}
