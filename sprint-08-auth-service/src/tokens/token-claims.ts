export interface TokenClaims {
  sub: string; // UUID
  accountId: number;
  roles: string[];
  iat: number;
  exp: number;
  iss: string;
}

export interface DecodedToken {
  sub: string;
  accountId: number;
  roles: string[];
  iat: number;
  exp: number;
  iss: string;
  iat_numeric?: number;
  exp_numeric?: number;
}
