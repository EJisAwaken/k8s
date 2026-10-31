package org.esoalahy.stock.modules.catalogue;

/**
 * EAN-13 internes : préfixe 200 (plage GS1 réservée à l'usage en magasin),
 * puis l'identifiant de la variante sur 9 chiffres, puis la clé de contrôle.
 * Imprimés en Code 128 sur les étiquettes, lus par toute douchette.
 */
public final class CodesBarres {

    private CodesBarres() {}

    public static String eanInterne(long varianteId) {
        String base = "200" + String.format("%09d", varianteId);
        return base + cle(base);
    }

    static int cle(String douzeChiffres) {
        int somme = 0;
        for (int i = 0; i < 12; i++) {
            int chiffre = douzeChiffres.charAt(i) - '0';
            somme += (i % 2 == 0) ? chiffre : chiffre * 3;
        }
        return (10 - somme % 10) % 10;
    }
}
