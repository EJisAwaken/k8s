package org.esoalahy.stock.core.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Traduit les erreurs en messages destinés à un utilisateur, pas à un
 * développeur : le front affiche le champ {@code detail} tel quel dans une
 * alerte. Deux règles en découlent — pas de jargon (codes HTTP, noms de champs
 * techniques, texte anglais), et une erreur de saisie ne doit jamais être
 * présentée comme une panne de l'application.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Introuvable");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /**
     * URL inconnue (aucun handler ni ressource statique). Sans ce handler,
     * la NoResourceFoundException est captée par le handler générique et
     * renvoyée en 500 au lieu de 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Introuvable");
        // Le chemin d'API ne dit rien à l'utilisateur : on le journalise, on ne l'affiche pas.
        log.debug("URL inconnue : /{}", ex.getResourcePath());
        pd.setDetail("Cette page ou cette donnée n'existe pas ou n'est plus disponible.");
        return pd;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflit");
        pd.setDetail(ex.getMessage());
        // code machine STABLE quand le refus appelle un geste précis
        // (aujourd'hui le seul : l'empreinte de corps de V109). Absent pour
        // tous les autres conflits — l'écran s'en tient alors au `detail`.
        if (ex.getCode() != null) pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Accès refusé");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /**
     * Droits insuffisants (@PreAuthorize / method security). Sans ce handler,
     * l'AuthorizationDeniedException est captée par le handler générique et
     * renvoyée en 500 au lieu de 403.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Accès refusé");
        pd.setDetail("Vous n'avez pas les droits nécessaires pour cette action.");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Requête invalide");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(TwoFactorRequiredException.class)
    public ProblemDetail handleTwoFactorRequired(TwoFactorRequiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Double authentification");
        pd.setDetail(ex.getMessage());
        pd.setProperty("code", TwoFactorRequiredException.CODE);
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Non autorisé");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.LOCKED);
        pd.setTitle("Compte verrouillé");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /**
     * Accès aux données déclenché avec une donnée invalide, typiquement un
     * {@code findById(null)} parce qu'un champ obligatoire du corps était absent
     * et non validé. C'est une requête malformée, pas une panne : 400 plutôt que
     * 500. Journalisé en WARN pour rester repérable (si c'est un vrai défaut, il
     * doit se voir dans les logs — on ne fait que soigner ce que voit l'utilisateur).
     */
    @ExceptionHandler(org.springframework.dao.InvalidDataAccessApiUsageException.class)
    public ProblemDetail handleInvalidDataAccess(org.springframework.dao.InvalidDataAccessApiUsageException ex) {
        log.warn("Accès aux données invalide (probable champ obligatoire manquant) : {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Données incomplètes");
        pd.setDetail("Une information nécessaire est manquante ou invalide. Vérifiez le formulaire et réessayez.");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        return invalidFields(ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldLabels.of(fe.getField()) + " : " + fe.getDefaultMessage())
                .distinct()
                .toList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return invalidFields(ex.getConstraintViolations().stream()
                .map(cv -> FieldLabels.of(cv.getPropertyPath().toString()) + " : " + cv.getMessage())
                .distinct()
                .toList());
    }

    /**
     * Validation au niveau méthode (Spring 6.1+) : @Valid sur un élément de liste,
     * @NotNull sur un @RequestParam, contraintes cascadées dans une collection…
     * Sans ce handler l'exception tombait dans le cas générique → 500 alors que
     * c'est une simple entrée invalide (400).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<String> problems = ex.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .toList();
        return problems.isEmpty()
                ? invalidFields(List.of("Certaines informations sont invalides"))
                : invalidFields(problems);
    }

    /** Date/heure saisie dans un format illisible (ex. « 16/07/2026 » au lieu d'ISO). */
    @ExceptionHandler(DateTimeParseException.class)
    public ProblemDetail handleDateTimeParse(DateTimeParseException ex) {
        log.debug("Date illisible : {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Date invalide");
        pd.setDetail("Une date saisie n'est pas dans un format valide. Vérifiez le formulaire et réessayez.");
        return pd;
    }

    /**
     * Violation de contrainte en base (unicité, clé étrangère, champ obligatoire
     * non validé en amont). Ce n'est pas une panne : 409 plutôt que 500. Le détail
     * SQL n'est jamais exposé (il peut contenir des données d'autrui).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violation d'intégrité en base : {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Opération impossible");
        pd.setDetail("Cette opération est en conflit avec des données existantes ou omet une information obligatoire. "
                + "Vérifiez le formulaire et réessayez.");
        return pd;
    }

    /**
     * Type de contenu inadapté (JSON envoyé à un endpoint multipart, ou en-tête
     * Content-Type absent). Sans ce handler l'exception remontait en 500.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.debug("Type de contenu non supporté : {}", ex.getContentType());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        pd.setTitle("Format non supporté");
        pd.setDetail("Le format des données envoyées n'est pas accepté par cette action. Rechargez la page et réessayez.");
        return pd;
    }

    /** « Départ : ce champ est obligatoire. » — le champ est nommé comme à l'écran. */
    private ProblemDetail invalidFields(List<String> problems) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Formulaire incomplet");
        pd.setDetail(problems.size() == 1
                ? problems.get(0) + "."
                : "Plusieurs informations sont à corriger — " + String.join(" ; ", problems) + ".");
        return pd;
    }

    /**
     * Filtre ou paramètre absent. Sans ce handler, l'utilisateur recevait
     * « Erreur interne » : une simple sélection manquante était présentée comme
     * une panne.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Information manquante");
        pd.setDetail(FieldLabels.of(ex.getParameterName())
                + " est nécessaire pour afficher ces données. Vérifiez votre sélection.");
        return pd;
    }

    /** Fichier attendu mais absent (envoi de pièce jointe). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Information manquante");
        pd.setDetail(FieldLabels.of(ex.getRequestPartName()) + " est obligatoire.");
        return pd;
    }

    /** Valeur d'un type inattendu (une lettre là où un nombre ou une date est attendu). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Valeur invalide");
        pd.setDetail("La valeur « " + ex.getValue() + " » n'est pas valide pour "
                + FieldLabels.of(ex.getName()).toLowerCase() + ".");
        return pd;
    }

    /**
     * Verbe HTTP inadapté sur une route existante (GET sur une route POST-only…).
     * Sans ce handler, l'exception tombait dans le cas générique et renvoyait 500
     * — un défaut client déguisé en panne serveur.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Méthode {} non supportée sur cette route", ex.getMethod());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        pd.setTitle("Action non disponible");
        pd.setDetail("Cette action n'est pas disponible ici. Rechargez la page et réessayez.");
        return pd;
    }

    /** Corps de requête illisible : bug côté client, ou requête forgée à la main. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Corps de requête illisible : {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Données invalides");
        pd.setDetail("Les données envoyées n'ont pas pu être lues. Rechargez la page et réessayez.");
        return pd;
    }

    /** Pièce jointe trop lourde — la limite vient de la configuration du serveur. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setTitle("Fichier trop volumineux");
        pd.setDetail("Ce fichier est trop lourd pour être envoyé. Réduisez sa taille et réessayez.");
        return pd;
    }

    /**
     * Dernier recours : la cause est un vrai défaut de l'application, pas une
     * erreur de l'utilisateur. On ne l'expose jamais (elle peut contenir des
     * détails techniques ou des données d'autrui) mais on la journalise.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erreur non gérée", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Problème technique");
        pd.setDetail("L'opération n'a pas pu aboutir en raison d'un problème technique. "
                + "Réessayez dans un instant ; si cela persiste, signalez-le à l'administrateur.");
        return pd;
    }
}
