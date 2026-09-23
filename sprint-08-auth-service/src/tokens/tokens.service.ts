import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { v4 as uuidv4 } from 'uuid';
import * as crypto from 'crypto';
import { ConfigurationService } from '../config/configuration.service';
import { User } from '../database/entities/user.entity';
import { TokenClaims, DecodedToken } from './token-claims';
import { TokenResponseDto } from './dto/token-response.dto';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class TokenService {
  private readonly logger = new JsonLogger('TokenService');

  constructor(
    private jwtService: JwtService,
    private configService: ConfigurationService,
  ) {}

  /**
   * Issue an access token (JWT).
   * Valid for configurable lifetime (default 900 seconds = 15 min).
   */
  private async issueAccessToken(user: User): Promise<string> {
    const payload: Partial<TokenClaims> = {
      sub: user.id,
      accountId: user.account_id,
      roles: user.roles,
    };

    const token = await this.jwtService.signAsync(payload);
    this.logger.log(`Access token issued for user: ${user.id}`);
    return token;
  }


  /**
   * Verify an access token and return its claims.
   * Validates signature, expiry, and issuer.
   */
  async verifyAccessToken(token: string): Promise<DecodedToken | null> {
    try {
      const decoded = await this.jwtService.verifyAsync(token, {
        secret: this.configService.getJwtSecret(),
        issuer: this.configService.getJwtIssuer(),
      });

      // Validate required claims
      if (
        !decoded.sub ||
        !decoded.accountId ||
        !decoded.roles ||
        !Array.isArray(decoded.roles) ||
        decoded.roles.length === 0 ||
        !decoded.iat ||
        !decoded.exp ||
        !decoded.iss
      ) {
        this.logger.warn('Token missing required claims');
        return null;
      }

      return decoded as DecodedToken;
    } catch (error) {
      this.logger.warn(`Token verification failed: ${error instanceof Error ? error.message : String(error)}`);
      return null;
    }
  }

}
