import { Injectable, CanActivate, ExecutionContext, UnauthorizedException } from '@nestjs/common';
import { Request } from 'express';
import { TokenService } from '../tokens/tokens.service';
import { DecodedToken } from '../tokens/token-claims';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private tokenService: TokenService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();
    const token = this.extractTokenFromHeader(request);

    if (!token) {
      throw new UnauthorizedException('Missing authorization header');
    }

    try {
      const decoded = await this.tokenService.verifyAccessToken(token);
      if (!decoded) {
        throw new UnauthorizedException('Invalid token');
      }
      // Attach decoded token to request for use in controllers
      (request as any).user = decoded;
    } catch (error) {
      throw new UnauthorizedException('Invalid token');
    }

    return true;
  }

  private extractTokenFromHeader(request: Request): string | undefined {
    const [type, token] = (request.headers.authorization || '').split(' ') ?? [];
    return type === 'Bearer' ? token : undefined;
  }
}
