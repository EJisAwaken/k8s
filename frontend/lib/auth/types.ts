/** Rôles du cahier des charges (§2) — miroir de `core/user/Role.java`. */
export const ROLES = ["ADMIN", "ACHATS", "MAGASINIER", "COMMERCIAL", "COMPTABLE"] as const;

export type Role = (typeof ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Administrateur",
  ACHATS: "Responsable achats",
  MAGASINIER: "Magasinier",
  COMMERCIAL: "Commercial",
  COMPTABLE: "Comptable / gérant",
};

export function isRole(value: unknown): value is Role {
  return typeof value === "string" && (ROLES as readonly string[]).includes(value);
}

export interface SessionUser {
  userId: number;
  email: string;
  fullName: string;
  role: Role;
  passwordExpired: boolean;
  twoFactorSetupRequired: boolean;
}

export interface BackendAuthResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  userId: number;
  email: string;
  fullName: string;
  role: Role;
  passwordExpired: boolean;
  twoFactorSetupRequired: boolean;
}

export function toSessionUser(auth: BackendAuthResponse): SessionUser {
  return {
    userId: auth.userId,
    email: auth.email,
    fullName: auth.fullName,
    role: auth.role,
    passwordExpired: auth.passwordExpired,
    twoFactorSetupRequired: auth.twoFactorSetupRequired,
  };
}
