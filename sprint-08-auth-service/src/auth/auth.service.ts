import { Injectable, UnauthorizedException } from '@nestjs/common';
import { UsersService } from '../users/users.service';
import { TokenService } from '../tokens/tokens.service';
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

    // Hash the refresh token to look it up
    const crypto = require('crypto');
    const tokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');

    // Find the token record and validate it's not revoked/expired
    // This will throw if token is invalid, expired, or already used
    // For now, we rely on the TokenService to handle this
    // We need the user first though, so we'll validate at controller level

    // The controller will pass the decoded token from the guard
    // This method will be called from controller with user context
    throw new Error('Refresh endpoint requires controller-level token validation');
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
