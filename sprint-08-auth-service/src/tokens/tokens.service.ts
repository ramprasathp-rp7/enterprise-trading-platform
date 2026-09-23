import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { v4 as uuidv4 } from 'uuid';
import * as crypto from 'crypto';
import { ConfigurationService } from '../config/configuration.service';
import { User } from '../database/entities/user.entity';
import { TokenClaims, DecodedToken } from './token-claims';
import { TokenResponseDto } from './dto/token-response.dto';
import { JsonLogger } from '../common/logging/json.logger';
import { RefreshTokenRepository } from '@/database/repositories/refresh-token.repository';

@Injectable()
export class TokenService {
  private readonly logger = new JsonLogger('TokenService');

  constructor(
    private jwtService: JwtService,
    private configService: ConfigurationService,
    private refreshTokenRepository: RefreshTokenRepository,
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

  private async issueRefreshToken(user: User): Promise<{ refreshToken: string; opaque: string }> {
    const opaque = uuidv4() + '-' + crypto.randomBytes(32).toString('hex');
    const refreshToken = this.hashToken(opaque);
    const familyId = uuidv4();
    const now = new Date();
    const expiresIn = this.configService.getRefreshTokenLifetime();
    const expiresOn = new Date(now.getTime() + expiresIn * 1000);

    // Store in database
    await this.refreshTokenRepository.saveToken({
      user_id: user.id,
      token_hash: refreshToken,
      family_id: familyId,
      issued_on: now,
      expires_on: expiresOn,
      revoked_on: null,
      replaced_by: null,
    });

    this.logger.log(`Refresh token issued for user: ${user.id}, family: ${familyId}`);

    return { refreshToken, opaque };
  }

  /**
   * Issue a new token pair (access + refresh).
   * Returns TokenResponseDto with both tokens.
   */
  async issueTokenPair(user: User): Promise<TokenResponseDto> {
    const accessToken = await this.issueAccessToken(user);
    const { refreshToken, opaque } = await this.issueRefreshToken(user);

    const expiresIn = this.configService.getAccessTokenLifetime();

    this.logger.log(`Token pair issued for user: ${user.id}`);

    return {
      accessToken,
      refreshToken: opaque, // Return the opaque token, not the hash
      tokenType: 'Bearer',
      expiresIn,
    };
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

  /**
   * Rotate a refresh token by its opaque value: consume the old one, issue a new pair.
   * Validates that the token exists, is not revoked, and hasn't expired.
   * Returns new TokenResponseDto.
   */
  
  async rotateRefreshTokenByOpaque(
    opaqueToken: string,
    user: User,
  ): Promise<TokenResponseDto> {
    // Hash the opaque token to look it up
    const tokenHash = this.hashToken(opaqueToken);

    // Find the old token
    const oldToken = await this.refreshTokenRepository.findByHash(tokenHash);

    if (!oldToken) {
      this.logger.warn(`Refresh token not found for user: ${user.id}`);
      throw new Error('Refresh token not found');
    }

    if (oldToken.revoked_on) {
      this.logger.warn(`Refresh token already revoked for user: ${user.id}`);
      throw new Error('Refresh token has been revoked or already used');
    }

    if (new Date() > oldToken.expires_on) {
      this.logger.warn(`Refresh token expired for user: ${user.id}`);
      throw new Error('Refresh token has expired');
    }

    // Mark the old token as revoked
    await this.refreshTokenRepository.markRevoked(oldToken.id);

    // Issue new pair
    const newTokenPair = await this.issueTokenPair(user);

    this.logger.log(`Refresh token rotated for user: ${user.id}`);

    return newTokenPair;
  }

  /**
   * Hash a refresh token for storage.
   * Never store the plaintext opaque token in the database.
   */
  private hashToken(token: string): string {
    return crypto.createHash('sha256').update(token).digest('hex');
  }
}



