import {
  Controller,
  Post,
  Get,
  Body,
  UseGuards,
  ConflictException,
  UnauthorizedException,
  UnprocessableEntityException,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { ThrottlerGuard } from '@nestjs/throttler';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiResponse } from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { RegisterDto } from '../users/dto/register.dto';
import { LoginDto } from '../users/dto/login.dto';
import { RefreshDto } from '../tokens/dto/token-response.dto';
import { UserResponseDto } from '../users/dto/user-response.dto';
import { TokenResponseDto } from '../tokens/dto/token-response.dto';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { DecodedToken } from '../tokens/token-claims';
import { TokenService } from '../tokens/tokens.service';
import { UsersService } from '../users/users.service';
import { RefreshTokenRepository } from '../database/repositories/refresh-token.repository';
import { AccountIdAlreadyExistsError, UnknownAccountIdError, UsernameAlreadyExistsError } from '../users/users.errors';
import * as crypto from 'crypto';

@ApiTags('Auth')
@Controller('auth')
export class AuthController {
  constructor(
    private authService: AuthService,
    private tokenService: TokenService,
    private usersService: UsersService,
    private refreshTokenRepository: RefreshTokenRepository,
  ) {}

  /**
   * POST /auth/register
   * Register a new user.
   * Does not issue tokens (client logs in separately).
   */
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Register a new user' })
  @ApiResponse({
    status: 201,
    description: 'User created',
    type: UserResponseDto,
  })
  @ApiResponse({
    status: 409,
    description: 'Username already taken',
    schema: { example: { errorCode: 'AUTH-409', message: 'Registration failed' } },
  })
  @ApiResponse({
    status: 422,
    description: 'Validation failed',
    schema: { example: { errorCode: 'VAL-422', message: 'Validation error' } },
  })
  async register(@Body() registerDto: RegisterDto): Promise<UserResponseDto> {
    try {
      return await this.authService.register(registerDto);
    } catch (error) {
      if (error instanceof UsernameAlreadyExistsError || error instanceof AccountIdAlreadyExistsError) {
        throw new ConflictException('Registration failed');
      }
      // The contract: registering against an unknown accountId fails validation (422 VAL-422)
      if (error instanceof UnknownAccountIdError) {
        throw new UnprocessableEntityException('accountId does not refer to a trading account');
      }
      throw error;
    }
  }

  /**
   * POST /auth/login
   * Login and receive access and refresh tokens.
   * Throttled: 5 attempts per minute per IP.
   */
  @Post('login')
  @UseGuards(ThrottlerGuard)
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Login and receive tokens' })
  @ApiResponse({
    status: 200,
    description: 'Authenticated',
    type: TokenResponseDto,
  })
  @ApiResponse({
    status: 401,
    description: 'Authentication failed',
    schema: { example: { errorCode: 'AUTH-401', message: 'Unauthorised' } },
  })
  @ApiResponse({
    status: 422,
    description: 'Validation failed',
    schema: { example: { errorCode: 'VAL-422', message: 'Validation error' } },
  })
  async login(@Body() loginDto: LoginDto): Promise<TokenResponseDto> {
    return this.authService.login(loginDto);
  }

  /**
   * POST /auth/refresh
   * Exchange refresh token for new access + refresh token pair.
   * Refresh tokens are rotated on every use.
   */
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Exchange refresh token for new token pair' })
  @ApiResponse({
    status: 200,
    description: 'New token pair',
    type: TokenResponseDto,
  })
  @ApiResponse({
    status: 401,
    description: 'Authentication failed',
    schema: { example: { errorCode: 'AUTH-401', message: 'Unauthorised' } },
  })
  @ApiResponse({
    status: 422,
    description: 'Validation failed',
    schema: { example: { errorCode: 'VAL-422', message: 'Validation error' } },
  })
  async refresh(@Body() refreshDto: RefreshDto): Promise<TokenResponseDto> {
    return this.authService.refresh(refreshDto.refreshToken);
  }

  /**
   * GET /auth/me
   * Get authenticated user profile.
   * Protected: requires valid JWT bearer token.
   */
  @Get('me')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get authenticated user' })
  @ApiResponse({
    status: 200,
    description: 'The authenticated user',
    type: UserResponseDto,
  })
  @ApiResponse({
    status: 401,
    description: 'Unauthorized',
    schema: { example: { errorCode: 'AUTH-401', message: 'Unauthorised' } },
  })
  async getCurrentUser(@CurrentUser() user: DecodedToken): Promise<UserResponseDto> {
    try {
      return await this.authService.getCurrentUser(user.sub);
    } catch (error) {
      throw new UnauthorizedException('Unauthorised');
    }
  }
}
