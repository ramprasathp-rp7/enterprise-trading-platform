import { Role } from '../common/roles.enum';

export interface TokenClaims {
  sub: string; // UUID
  accountId: number;
  roles: Role[];
  iat: number;
  exp: number;
  iss: string;
}

export interface DecodedToken {
  sub: string;
  accountId: number;
  roles: Role[];
  iat: number;
  exp: number;
  iss: string;
  iat_numeric?: number;
  exp_numeric?: number;
}
