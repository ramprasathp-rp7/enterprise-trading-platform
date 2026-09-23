import { IsString, IsInt, IsArray, MinLength } from 'class-validator';

export class TokenResponseDto {
  @IsString()
  @MinLength(1)
  accessToken: string;

  @IsString()
  @MinLength(1)
  refreshToken: string;

  @IsString()
  tokenType: string; // Always "Bearer"

  @IsInt()
  expiresIn: number; // Seconds (900 for access token)
}

export class RefreshDto {
  @IsString()
  @MinLength(1)
  refreshToken: string;
}
