/** Messages d'erreur génériques partagés par le BFF et le client API. */
export const messages = {
  network: "Le serveur est injoignable. Vérifiez votre connexion, puis réessayez.",
  sessionExpired: "Votre session a expiré. Reconnectez-vous.",
  missingFields: "Saisissez votre e-mail et votre mot de passe.",
  forbidden: "Vous n'avez pas les droits nécessaires pour cette action.",
  technical: "L'opération n'a pas pu aboutir. Réessayez.",
} as const;
