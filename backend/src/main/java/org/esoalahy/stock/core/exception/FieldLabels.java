package org.esoalahy.stock.core.exception;

import java.util.Map;

/**
 * Traduit le nom technique d'un champ en libellé lisible.
 *
 * <p>Les erreurs de validation sont affichées telles quelles à l'utilisateur :
 * sans cette table, un magasinier lirait « varianteId: must not be null ».
 * Les libellés reprennent ceux des formulaires.
 */
final class FieldLabels {

    private FieldLabels() {}

    private static final Map<String, String> LABELS = Map.ofEntries(
            // Identité & compte
            Map.entry("fullName", "Nom complet"),
            Map.entry("phone", "Téléphone"),
            Map.entry("email", "E-mail"),
            Map.entry("password", "Mot de passe"),
            Map.entry("currentPassword", "Mot de passe actuel"),
            Map.entry("newPassword", "Nouveau mot de passe"),
            Map.entry("role", "Rôle"),
            Map.entry("code", "Code"),

            // Catalogue
            Map.entry("reference", "Référence"),
            Map.entry("nom", "Nom"),
            Map.entry("nomFournisseur", "Nom fournisseur"),
            Map.entry("referenceFournisseur", "Référence fournisseur"),
            Map.entry("categorieId", "Catégorie"),
            Map.entry("unite", "Unité"),
            Map.entry("poidsKg", "Poids (kg)"),
            Map.entry("volumeM3", "Volume (m³)"),
            Map.entry("codeSh", "Code SH douanier"),
            Map.entry("suiviSerie", "Suivi par numéro de série"),
            Map.entry("produitId", "Produit"),
            Map.entry("varianteId", "Variante"),
            Map.entry("sku", "SKU"),
            Map.entry("attributs", "Attributs"),
            Map.entry("codeBarres", "Code-barres"),
            Map.entry("prixDetail", "Prix détail"),
            Map.entry("prixGros", "Prix de gros"),
            Map.entry("prixRevendeur", "Prix revendeur"),
            Map.entry("seuilGros", "Seuil de quantité pour le prix de gros"),
            Map.entry("seuilMini", "Seuil minimum"),

            // Dépôts & stock
            Map.entry("depotId", "Dépôt"),
            Map.entry("depotSourceId", "Dépôt de départ"),
            Map.entry("depotDestinationId", "Dépôt d'arrivée"),
            Map.entry("emplacementId", "Emplacement"),
            Map.entry("adresse", "Adresse"),
            Map.entry("quantite", "Quantité"),
            Map.entry("quantiteComptee", "Quantité comptée"),
            Map.entry("coutUnitaire", "Coût unitaire"),
            Map.entry("type", "Type"),
            Map.entry("motif", "Motif"),
            Map.entry("lignes", "Lignes"),
            Map.entry("numerosSerie", "Numéros de série"),
            Map.entry("perimetre", "Périmètre"),

            // Génériques
            Map.entry("date", "Date"),
            Map.entry("from", "Date de début"),
            Map.entry("to", "Date de fin"),
            Map.entry("label", "Libellé"),
            Map.entry("name", "Nom"),
            Map.entry("description", "Description"),
            Map.entry("commentaire", "Commentaire"),
            Map.entry("status", "Statut"),
            Map.entry("statut", "Statut"),
            Map.entry("file", "Fichier"),
            Map.entry("q", "Recherche")
    );

    /**
     * Libellé du champ. À défaut de traduction connue, on renvoie le nom
     * technique nettoyé plutôt qu'un texte inventé.
     */
    static String of(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return "Champ";
        // « lignes[0].quantite » → on ne garde que le dernier segment
        String field = fieldName.substring(fieldName.lastIndexOf('.') + 1).replaceAll("\\[\\d+]", "");
        String known = LABELS.get(field);
        if (known != null) return known;

        // Repli : « depotId » → « Depot », « maxAttempts » → « Max attempts »
        String cleaned = field.endsWith("Id") && field.length() > 2
                ? field.substring(0, field.length() - 2)
                : field;
        String spaced = cleaned.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
        return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
    }
}
