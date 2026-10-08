import { UsersService } from './users.service';
import { AccountIdAlreadyExistsError, UsernameAlreadyExistsError } from './users.errors';
import { Role } from '../common/roles.enum';

describe('UsersService', () => {
  let svc: UsersService;

  const usersRepo: any = { findByUsername: jest.fn(), findById: jest.fn(), findOne: jest.fn(), saveUser: jest.fn() };
  const accountIdsRepo: any = { accountIdExists: jest.fn() };
  const passwordService: any = { hashPassword: jest.fn(), verifyPassword: jest.fn(), verifyAgainstDummy: jest.fn() };

  const dto: any = { username: 'alice', password: 'longenoughpassword', accountId: 7, roles: [Role.ADMIN] };

  beforeEach(() => {
    jest.resetAllMocks();
    jest.spyOn(console, 'log').mockImplementation(() => undefined);
    jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    svc = new UsersService(usersRepo, accountIdsRepo, passwordService);
  });

  afterEach(() => jest.restoreAllMocks());

  describe('register', () => {
    test('creates a CUSTOMER user and ignores requested roles', async () => {
      const createdOn = new Date('2026-01-01T00:00:00Z');
      usersRepo.findByUsername.mockResolvedValueOnce(null);
      accountIdsRepo.accountIdExists.mockResolvedValueOnce(true);
      usersRepo.findOne.mockResolvedValueOnce(null);
      passwordService.hashPassword.mockResolvedValueOnce('hashed');
      usersRepo.saveUser.mockResolvedValueOnce({
        id: 'u1',
        username: 'alice',
        account_id: 7,
        roles: [Role.CUSTOMER],
        created_on: createdOn,
      });

      const result = await svc.register(dto);

      expect(passwordService.hashPassword).toHaveBeenCalledWith('longenoughpassword');
      expect(usersRepo.saveUser).toHaveBeenCalledWith({
        username: 'alice',
        password_hash: 'hashed',
        account_id: 7,
        roles: [Role.CUSTOMER],
      });
      expect(result).toEqual({ id: 'u1', username: 'alice', accountId: 7, roles: [Role.CUSTOMER], createdOn });
    });

    test('rejects a duplicate username', async () => {
      usersRepo.findByUsername.mockResolvedValueOnce({ id: 'existing' });
      await expect(svc.register(dto)).rejects.toBeInstanceOf(UsernameAlreadyExistsError);
      expect(accountIdsRepo.accountIdExists).not.toHaveBeenCalled();
    });

    test('rejects an unknown account id', async () => {
      usersRepo.findByUsername.mockResolvedValueOnce(null);
      accountIdsRepo.accountIdExists.mockResolvedValueOnce(false);
      await expect(svc.register(dto)).rejects.toThrow('Account ID 7 does not exist');
      expect(usersRepo.saveUser).not.toHaveBeenCalled();
    });

    test('rejects an account id already linked to another user', async () => {
      usersRepo.findByUsername.mockResolvedValueOnce(null);
      accountIdsRepo.accountIdExists.mockResolvedValueOnce(true);
      usersRepo.findOne.mockResolvedValueOnce({ id: 'other' });
      await expect(svc.register(dto)).rejects.toBeInstanceOf(AccountIdAlreadyExistsError);
      expect(usersRepo.findOne).toHaveBeenCalledWith({ where: { account_id: 7 } });
      expect(passwordService.hashPassword).not.toHaveBeenCalled();
    });
  });

  test('findByUsername delegates to the repository', async () => {
    const user = { id: 'u1' };
    usersRepo.findByUsername.mockResolvedValueOnce(user);
    await expect(svc.findByUsername('alice')).resolves.toBe(user);
  });

  test('findById delegates to the repository', async () => {
    usersRepo.findById.mockResolvedValueOnce(null);
    await expect(svc.findById('missing')).resolves.toBeNull();
    expect(usersRepo.findById).toHaveBeenCalledWith('missing');
  });

  describe('verifyPassword', () => {
    test('checks against a dummy hash when the user is unknown', async () => {
      passwordService.verifyAgainstDummy.mockResolvedValueOnce(false);
      await expect(svc.verifyPassword(null, 'pw')).resolves.toBe(false);
      expect(passwordService.verifyAgainstDummy).toHaveBeenCalledWith('pw');
      expect(passwordService.verifyPassword).not.toHaveBeenCalled();
    });

    test('returns true for a matching password', async () => {
      passwordService.verifyPassword.mockResolvedValueOnce(true);
      await expect(svc.verifyPassword({ username: 'alice', password_hash: 'h' } as any, 'pw')).resolves.toBe(true);
      expect(passwordService.verifyPassword).toHaveBeenCalledWith('pw', 'h');
      expect(console.warn).not.toHaveBeenCalled();
    });

    test('returns false and logs a warning for a wrong password', async () => {
      passwordService.verifyPassword.mockResolvedValueOnce(false);
      await expect(svc.verifyPassword({ username: 'alice', password_hash: 'h' } as any, 'bad')).resolves.toBe(false);
      expect(console.warn).toHaveBeenCalled();
    });
  });
});
