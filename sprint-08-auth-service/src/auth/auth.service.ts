import { Injectable, UnauthorizedException } from '@nestjs/common';
import { UsersService } from '../users/users.service';
import { TokenService } from '../tokens/tokens.service';
import { RefreshTokenRepository } from '../database/repositories/refresh-token.repository';
import { RegisterDto } from '../users/dto/register.dto';
import { LoginDto } from '../users/dto/login.dto';
import { UserResponseDto } from '../users/dto/user-response.dto';
import { TokenResponseDto } from '../tokens/dto/token-response.dto';
import { UsernameAlreadyExistsError } from '../users/users.errors';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class AuthService {
  private readonly logger = new JsonLogger('AuthService');

  constructor(
    private usersService: UsersService,
    private tokenService: TokenService,
    private refreshTokenRepository: RefreshTokenRepository,
  ) {}

  /**
   * Register a new user.
   * Returns UserResponse (no tokens).
   */
  async register(registerDto: RegisterDto): Promise<UserResponseDto> {
    this.logger.log(`Registration request for username: ${registerDto.username}`);
    return this.usersService.register(registerDto);
  }

  /**
   * Login: verify credentials and issue token pair.
   * Returns TokenResponseDto.
   */
  async login(loginDto: LoginDto): Promise<TokenResponseDto> {
    const { username, password } = loginDto;

    this.logger.log(`Login attempt for username: ${username}`);

    // Find user by username
    const user = await this.usersService.findByUsername(username);

    // Verify password (timing-safe: dummy hash if user not found)
    const isValid = await this.usersService.verifyPassword(user, password);
    if (!isValid) {
      throw new UnauthorizedException('Unauthorised');
    }

    // At this point, user must not be null and password is valid
    if (!user) {
      throw new UnauthorizedException('Unauthorised');
    }

    // Issue token pair
    const tokenPair = await this.tokenService.issueTokenPair(user);

    this.logger.log(`User logged in: ${username}`);

    return tokenPair;
  }

  /**
   * Refresh: consume old refresh token, issue new pair.
   * Returns TokenResponseDto.
   */
  async refresh(refreshToken: string): Promise<TokenResponseDto> {
    this.logger.log(`Refresh token request`);

    const crypto = require('crypto');
    const tokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');

    // Look up the refresh token record
    const tokenRecord = await this.refreshTokenRepository.findByHash(tokenHash);
    if (!tokenRecord) {
      this.logger.warn('Refresh token not found');
      throw new UnauthorizedException('Unauthorised');
    }

    // Load user
    const user = await this.usersService.findById(tokenRecord.user_id);
    if (!user) {
      this.logger.warn('User not found for refresh token');
      throw new UnauthorizedException('Unauthorised');
    }

    // Defer rotation logic to TokenService
    return this.tokenService.rotateRefreshTokenByOpaque(refreshToken, user);
  }

  /**
   * Get authenticated user profile.
   * Called from /auth/me with valid JWT token.
   */
  async getCurrentUser(userId: string): Promise<UserResponseDto> {
    const user = await this.usersService.findById(userId);
    if (!user) {
      throw new Error('User not found');
    }

    // Convert to response DTO
    return {
      id: user.id,
      username: user.username,
      accountId: user.account_id,
      roles: user.roles,
      createdOn: user.created_on,
    };
  }
}
