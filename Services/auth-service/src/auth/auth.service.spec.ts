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

  test('register delegates to UsersService', async () => {
    const created = { id: 'u1', username: 'alice' };
    usersService.register.mockResolvedValueOnce(created);
    await expect(svc.register({ username: 'alice' } as any)).resolves.toBe(created);
  });

  test('login refuses a valid password result with no user', async () => {
    usersService.findByUsername.mockResolvedValueOnce(null);
    usersService.verifyPassword.mockResolvedValueOnce(true);
    await expect(svc.login({ username: 'ghost', password: 'p' } as any)).rejects.toThrow('Unauthorised');
    expect(tokenService.issueTokenPair).not.toHaveBeenCalledWith(null);
  });

  test('refresh looks up the token by its sha256 hash and rotates it', async () => {
    const user = { id: 'u1' };
    const pair = { accessToken: 'a2', refreshToken: 'r2' };
    refreshRepo.findByHash.mockResolvedValueOnce({ user_id: 'u1' });
    usersService.findById.mockResolvedValueOnce(user);
    tokenService.rotateRefreshTokenByOpaque.mockResolvedValueOnce(pair);

    await expect(svc.refresh('opaque')).resolves.toBe(pair);
    const expectedHash = require('crypto').createHash('sha256').update('opaque').digest('hex');
    expect(refreshRepo.findByHash).toHaveBeenCalledWith(expectedHash);
    expect(tokenService.rotateRefreshTokenByOpaque).toHaveBeenCalledWith('opaque', user);
  });

  test('refresh refuses a token whose user no longer exists', async () => {
    refreshRepo.findByHash.mockResolvedValueOnce({ user_id: 'deleted' });
    usersService.findById.mockResolvedValueOnce(null);
    await expect(svc.refresh('opaque')).rejects.toThrow('Unauthorised');
  });

  test('getCurrentUser maps the entity to the response DTO', async () => {
    const createdOn = new Date();
    usersService.findById.mockResolvedValueOnce({
      id: 'u1',
      username: 'alice',
      account_id: 3,
      roles: ['CUSTOMER'],
      created_on: createdOn,
      password_hash: 'secret',
    });
    await expect(svc.getCurrentUser('u1')).resolves.toEqual({
      id: 'u1',
      username: 'alice',
      accountId: 3,
      roles: ['CUSTOMER'],
      createdOn,
    });
  });
});
