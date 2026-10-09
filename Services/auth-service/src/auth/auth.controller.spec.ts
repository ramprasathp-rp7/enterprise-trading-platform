import { ConflictException, UnauthorizedException, UnprocessableEntityException } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AccountIdAlreadyExistsError, UnknownAccountIdError, UsernameAlreadyExistsError } from '../users/users.errors';

describe('AuthController', () => {
  let controller: AuthController;

  const authService: any = {
    register: jest.fn(),
    login: jest.fn(),
    refresh: jest.fn(),
    getCurrentUser: jest.fn(),
  };

  const tokenPair = { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 };

  beforeEach(() => {
    jest.resetAllMocks();
    controller = new AuthController(authService, {} as any, {} as any, {} as any);
  });

  describe('register', () => {
    const dto: any = { username: 'alice', password: 'longenoughpassword', accountId: 1 };

    test('returns the created user', async () => {
      const user = { id: 'u1', username: 'alice', accountId: 1, roles: ['CUSTOMER'], createdOn: new Date() };
      authService.register.mockResolvedValueOnce(user);
      await expect(controller.register(dto)).resolves.toBe(user);
      expect(authService.register).toHaveBeenCalledWith(dto);
    });

    test('maps duplicate username to ConflictException', async () => {
      authService.register.mockRejectedValueOnce(new UsernameAlreadyExistsError('alice'));
      await expect(controller.register(dto)).rejects.toBeInstanceOf(ConflictException);
    });

    test('maps duplicate account id to ConflictException', async () => {
      authService.register.mockRejectedValueOnce(new AccountIdAlreadyExistsError(1));
      await expect(controller.register(dto)).rejects.toBeInstanceOf(ConflictException);
    });

    test('maps an unknown account id to a 422 validation error', async () => {
      authService.register.mockRejectedValueOnce(new UnknownAccountIdError(99));
      await expect(controller.register(dto)).rejects.toBeInstanceOf(UnprocessableEntityException);
    });

    test('rethrows other errors unchanged', async () => {
      const err = new Error('database unavailable');
      authService.register.mockRejectedValueOnce(err);
      await expect(controller.register(dto)).rejects.toBe(err);
    });
  });

  test('login delegates to AuthService', async () => {
    authService.login.mockResolvedValueOnce(tokenPair);
    const dto: any = { username: 'alice', password: 'pw' };
    await expect(controller.login(dto)).resolves.toBe(tokenPair);
    expect(authService.login).toHaveBeenCalledWith(dto);
  });

  test('refresh passes the refresh token to AuthService', async () => {
    authService.refresh.mockResolvedValueOnce(tokenPair);
    await expect(controller.refresh({ refreshToken: 'opaque' } as any)).resolves.toBe(tokenPair);
    expect(authService.refresh).toHaveBeenCalledWith('opaque');
  });

  describe('getCurrentUser', () => {
    test('returns the profile for the token subject', async () => {
      const profile = { id: 'u1', username: 'alice' };
      authService.getCurrentUser.mockResolvedValueOnce(profile);
      await expect(controller.getCurrentUser({ sub: 'u1' } as any)).resolves.toBe(profile);
      expect(authService.getCurrentUser).toHaveBeenCalledWith('u1');
    });

    test('maps any failure to UnauthorizedException', async () => {
      authService.getCurrentUser.mockRejectedValueOnce(new Error('User not found'));
      await expect(controller.getCurrentUser({ sub: 'gone' } as any)).rejects.toBeInstanceOf(UnauthorizedException);
    });
  });
});
