package org.esoalahy.stock.core.user;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Compte utilisateur. L'e-mail sert d'identifiant de connexion et est unique.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 50)
    private String phone;

    /** Secret TOTP (2FA) — obligatoire pour l'administrateur (cahier §6). */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    /** Codes de secours 2FA, séparés par des virgules. */
    @Column(name = "recovery_codes", columnDefinition = "TEXT")
    private String recoveryCodes;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Date du dernier changement de mot de passe (expiration périodique). */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /**
     * Le compte porte un mot de passe TEMPORAIRE (généré à la création ou à la
     * réinitialisation par l'administrateur) : la première connexion doit le
     * remplacer.
     */
    @Builder.Default
    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 255)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
