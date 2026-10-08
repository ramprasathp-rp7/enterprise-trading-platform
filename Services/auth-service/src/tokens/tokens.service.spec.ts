import { TokenService } from './tokens.service';
import { JwtService } from '@nestjs/jwt';
import { ConfigurationService } from '../config/configuration.service';
import { RefreshTokenRepository } from '../database/repositories/refresh-token.repository';

// Use the real crypto implementation but override randomBytes for deterministic tests.
// Call jest.requireActual inside the factory to avoid hoisting initialization issues.
jest.mock('crypto', () => {
  const realCrypto = jest.requireActual('crypto');
  return {
    ...realCrypto,
    randomBytes: (n: number) => Buffer.alloc(n, 1),
  };
});

describe('TokenService', () => {
  let svc: TokenService;

  const jwtService = { signAsync: jest.fn().mockResolvedValue('jwt-token'), verifyAsync: jest.fn() } as unknown as JwtService;
  const config = { getAccessTokenLifetime: () => 900, getRefreshTokenLifetime: () => 604800, getJwtSecret: () => 'secret', getJwtIssuer: () => 'auth-service' } as unknown as ConfigurationService;
  const repo = { 
    saveToken: jest.fn().mockResolvedValue({ id: 'token-id' }), 
    findByHash: jest.fn(), 
    markRevoked: jest.fn(), 
    revokeFamily: jest.fn(),
    findByFamilyId: jest.fn(),
    markReplacedBy: jest.fn(),
  } as unknown as RefreshTokenRepository;

  beforeEach(() => {
    svc = new TokenService(jwtService, config, repo);
  });

  test('a token is issued with the contract claims and expiry', async () => {
      const user: any = { id: 'u1', account_id: 10, roles: ['user'] };
    const pair = await svc.issueTokenPair(user);
    expect(pair.accessToken).toBeDefined();
    expect(pair.refreshToken).toBeDefined();
    expect(pair.tokenType).toBe('Bearer');
    expect(pair.expiresIn).toBe(900);
  });

  test('verifyAccessToken validates claims and rejects malformed', async () => {
    (jwtService.verifyAsync as jest.Mock).mockResolvedValueOnce({ sub: 'u1', accountId: 10, roles: ['user'], iat: 1, exp: 2, iss: 'auth-service' });
    const ok = await svc.verifyAccessToken('token');
    expect(ok).not.toBeNull();

    (jwtService.verifyAsync as jest.Mock).mockImplementationOnce(() => { throw new Error('bad'); });
    const bad = await svc.verifyAccessToken('token');
    expect(bad).toBeNull();
  });

  test('rotate refresh returns a new access token and a new refresh token', async () => {
    const user: any = { id: 'u2', account_id: 15, roles: ['user'] };
    const familyId = 'family-123';
    // simulate existing token
    const old = { id: 'old-token-id', user_id: 'u2', token_hash: 'h', family_id: familyId, revoked_on: null, expires_on: new Date(Date.now() + 10000) };
    const newToken = { id: 'new-token-id', user_id: 'u2', token_hash: 'new-h', family_id: familyId, revoked_on: null, expires_on: new Date(Date.now() + 10000) };
    (repo.findByHash as jest.Mock).mockResolvedValueOnce(old);
    (repo.findByFamilyId as jest.Mock).mockResolvedValueOnce([old, newToken]);
    (repo.markReplacedBy as jest.Mock).mockResolvedValueOnce(undefined);
    (repo.markRevoked as jest.Mock).mockResolvedValueOnce(undefined);
    const result = await svc.rotateRefreshTokenByOpaque('opaque', user);
    expect(result.accessToken).toBeDefined();
    expect(result.refreshToken).toBeDefined();
    expect(repo.markReplacedBy).toHaveBeenCalledWith('old-token-id', 'new-token-id');
  });

  test('rotate refresh rejects revoked or expired tokens', async () => {
    const user: any = { id: 'u3', account_id: 20, roles: ['user'] };
    (repo.findByHash as jest.Mock).mockResolvedValueOnce(null);
    await expect(svc.rotateRefreshTokenByOpaque('x', user)).rejects.toThrow();
  });

  test('rotate refresh revokes entire token family on replay attempt', async () => {
    const user: any = { id: 'u4', account_id: 25, roles: ['user'] };
    const familyId = 'family-123';
    // simulate already-revoked token (replay attempt)
    const old = { id: 1, user_id: 'u4', token_hash: 'h', family_id: familyId, revoked_on: new Date(), expires_on: new Date(Date.now() + 10000) };
    (repo.findByHash as jest.Mock).mockResolvedValueOnce(old);
    (repo.revokeFamily as jest.Mock).mockResolvedValueOnce(undefined);
    
    await expect(svc.rotateRefreshTokenByOpaque('opaque', user)).rejects.toThrow();
    expect(repo.revokeFamily).toHaveBeenCalledWith(familyId);
  });

  test('rotate refresh rejects an expired token without revoking the family', async () => {
    const user: any = { id: 'u5', account_id: 30, roles: ['user'] };
    const old = { id: 'old', user_id: 'u5', token_hash: 'h', family_id: 'fam', revoked_on: null, expires_on: new Date(Date.now() - 1000) };
    (repo.findByHash as jest.Mock).mockResolvedValueOnce(old);
    (repo.revokeFamily as jest.Mock).mockClear();

    await expect(svc.rotateRefreshTokenByOpaque('opaque', user)).rejects.toThrow('Unauthorised');
    expect(repo.revokeFamily).not.toHaveBeenCalled();
  });

  const validClaims = { sub: 'u1', accountId: 10, roles: ['user'], iat: 1, exp: 2, iss: 'auth-service' };

  test.each([
    ['sub', { sub: undefined }],
    ['accountId (null)', { accountId: null }],
    ['accountId (undefined)', { accountId: undefined }],
    ['roles', { roles: undefined }],
    ['roles (not an array)', { roles: 'user' }],
    ['roles (empty)', { roles: [] }],
    ['iat', { iat: undefined }],
    ['exp', { exp: undefined }],
    ['iss', { iss: undefined }],
  ])('verifyAccessToken rejects a token missing %s', async (_name, override) => {
    (jwtService.verifyAsync as jest.Mock).mockResolvedValueOnce({ ...validClaims, ...override });
    await expect(svc.verifyAccessToken('token')).resolves.toBeNull();
  });

  test('verifyAccessToken accepts accountId 0', async () => {
    (jwtService.verifyAsync as jest.Mock).mockResolvedValueOnce({ ...validClaims, accountId: 0 });
    await expect(svc.verifyAccessToken('token')).resolves.not.toBeNull();
  });

  test('verifyAccessToken handles non-Error throws', async () => {
    (jwtService.verifyAsync as jest.Mock).mockRejectedValueOnce('weird');
    await expect(svc.verifyAccessToken('token')).resolves.toBeNull();
  });
});
