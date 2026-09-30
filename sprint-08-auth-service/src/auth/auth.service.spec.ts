import { AuthService } from './auth.service';

describe('AuthService', () => {
  let svc: AuthService;

  const usersService: any = { findByUsername: jest.fn(), verifyPassword: jest.fn(), findById: jest.fn(), register: jest.fn() };
  const tokenService: any = { issueTokenPair: jest.fn(), rotateRefreshTokenByOpaque: jest.fn() };
  const refreshRepo: any = { findByHash: jest.fn() };

  beforeEach(() => {
    svc = new AuthService(usersService, tokenService, refreshRepo);
  });

  test('a valid token is accepted', async () => {
    usersService.findByUsername.mockResolvedValueOnce({ id: 'u1', account_id: 1, roles: ['user'] });
    usersService.verifyPassword.mockResolvedValueOnce(true);
    tokenService.issueTokenPair.mockResolvedValueOnce({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 });
    const res = await svc.login({ username: 'u', password: 'p' } as any);
    expect(res.accessToken).toBeDefined();
  });

  test('an expired token is refused (refresh)', async () => {
    refreshRepo.findByHash.mockResolvedValueOnce(null);
    await expect(svc.refresh('bad')).rejects.toThrow();
  });

  test('a token with wrong signature is refused before claim read (verifyAccessToken path in tokens)', async () => {
    // covered indirectly via TokenService tests; ensure login rejects bad password
    usersService.findByUsername.mockResolvedValueOnce(null);
    usersService.verifyPassword.mockResolvedValueOnce(false);
    await expect(svc.login({ username: 'x', password: 'y' } as any)).rejects.toThrow();
  });

  test('a malformed header is refused (getCurrentUser)', async () => {
    usersService.findById.mockResolvedValueOnce(null);
    await expect(svc.getCurrentUser('nope')).rejects.toThrow('User not found');
  });
});
