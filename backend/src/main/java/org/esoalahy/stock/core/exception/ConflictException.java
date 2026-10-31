package org.esoalahy.stock.core.exception;

/**
 * Conflit métier — 409, message destiné à l'utilisateur final, en français.
 *
 * <h2>un CODE facultatif, à côté de la phrase</h2>
 * <p>Un refus se lit ; certains se <b>traitent</b>. La file hors ligne
 * ({@code frontend/lib/offline/sync.ts}) range tout 409 dans son panneau de
 * conflits et n'en rejoue aucun — c'est juste —, mais elle ne peut pas dire au
 * personnel <b>quoi faire ensuite</b> tant que le seul signal disponible est
 * une phrase française. Mesuré le 2026-08-20 sur le 409 d'empreinte de V109 :
 * la réponse porte {@code type: about:blank} et rien d'autre que son
 * {@code detail}. Faire reconnaître ce cas au navigateur imposerait de
 * reconnaître un morceau de prose — un texte qu'on réécrira un jour pour le
 * rendre plus clair, et qui cassera l'écran en silence ce jour-là.</p>
 *
 * <p>D'où ce {@code code} : <b>facultatif</b> (les dizaines de
 * {@code new ConflictException(message)} du produit ne bougent pas d'une
 * ligne), <b>stable</b>, et rendu en propriété du {@code ProblemDetail} par
 * {@link GlobalExceptionHandler}. Il ne remplace jamais la phrase : c'est
 * toujours {@code detail} qui s'affiche, le code ne sert qu'à choisir le
 * GESTE proposé à côté.</p>
 */
public class ConflictException extends RuntimeException {

    /**
     * Le contenu envoyé sous une clé d'idempotence déjà connue diffère du
     * premier envoi (V109) : la correction faite hors ligne n'a pas été
     * enregistrée, et le seul remède est de REFAIRE le geste.
     */
    public static final String CODE_IDEMPOTENCE_CORPS_DIFFERENT = "IDEMPOTENCE_CORPS_DIFFERENT";

    /** Code machine stable, ou {@code null} — l'écrasante majorité des conflits. */
    private final String code;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
