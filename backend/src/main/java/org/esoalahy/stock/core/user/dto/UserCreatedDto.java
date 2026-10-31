package org.esoalahy.stock.core.user.dto;

/**
 * Réponse d'une création ou d'une réinitialisation : le mot de passe
 * temporaire n'est rendu qu'UNE fois, jamais journalisé ni stocké en clair.
 */
public record UserCreatedDto(UserDto user, String temporaryPassword) {}
