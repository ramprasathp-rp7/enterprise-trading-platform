import { UnauthorizedException } from '@nestjs/common';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('JwtAuthGuard', () => {
  const tokenService: any = { verifyAccessToken: jest.fn() };
  let guard: JwtAuthGuard;

  const contextFor = (headers: Record<string, string>) => {
    const request: any = { headers };
    const context: any = { switchToHttp: () => ({ getRequest: () => request }) };
    return { request, context };
  };

  beforeEach(() => {
    jest.resetAllMocks();
    guard = new JwtAuthGuard(tokenService);
  });

  test('accepts a valid bearer token and attaches the decoded claims', async () => {
    const decoded = { sub: 'u1', accountId: 1, roles: ['CUSTOMER'] };
    tokenService.verifyAccessToken.mockResolvedValueOnce(decoded);
    const { request, context } = contextFor({ authorization: 'Bearer good-token' });

    await expect(guard.canActivate(context)).resolves.toBe(true);
    expect(tokenService.verifyAccessToken).toHaveBeenCalledWith('good-token');
    expect(request.user).toBe(decoded);
  });

  test('rejects a request with no authorization header', async () => {
    const { context } = contextFor({});
    await expect(guard.canActivate(context)).rejects.toThrow(new UnauthorizedException('Missing authorization header'));
    expect(tokenService.verifyAccessToken).not.toHaveBeenCalled();
  });

  test('rejects a non-Bearer scheme', async () => {
    const { context } = contextFor({ authorization: 'Basic abc' });
    await expect(guard.canActivate(context)).rejects.toThrow('Missing authorization header');
  });

  test('rejects a token that fails verification', async () => {
    tokenService.verifyAccessToken.mockResolvedValueOnce(null);
    const { request, context } = contextFor({ authorization: 'Bearer bad' });
    await expect(guard.canActivate(context)).rejects.toThrow('Invalid token');
    expect(request.user).toBeUndefined();
  });

  test('rejects when verification throws', async () => {
    tokenService.verifyAccessToken.mockRejectedValueOnce(new Error('jwt malformed'));
    const { context } = contextFor({ authorization: 'Bearer junk' });
    await expect(guard.canActivate(context)).rejects.toThrow('Invalid token');
  });
});
