import * as argon2 from 'argon2';
import { PasswordService } from './password.service';

jest.mock('argon2', () => ({
  argon2id: 2,
  hash: jest.fn(),
  verify: jest.fn(),
}));

// Failure paths need argon2 to throw, so it is mocked here; the real hashing is covered in password.service.spec.ts.
describe('PasswordService failure handling', () => {
  const svc = new PasswordService();
  const hash = argon2.hash as jest.Mock;
  const verify = argon2.verify as jest.Mock;

  beforeEach(() => {
    jest.resetAllMocks();
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => jest.restoreAllMocks());

  test('hashPassword wraps argon2 errors in a generic error', async () => {
    hash.mockRejectedValueOnce(new Error('out of memory'));
    await expect(svc.hashPassword('pw')).rejects.toThrow('Password hashing failed');
    expect(console.error).toHaveBeenCalled();
  });

  test('hashPassword handles non-Error rejections', async () => {
    hash.mockRejectedValueOnce('weird');
    await expect(svc.hashPassword('pw')).rejects.toThrow('Password hashing failed');
  });

  test('verifyPassword returns false when argon2 throws', async () => {
    verify.mockRejectedValueOnce(new Error('invalid hash'));
    await expect(svc.verifyPassword('pw', 'not-a-hash')).resolves.toBe(false);
  });

  test('verifyPassword handles non-Error rejections', async () => {
    verify.mockRejectedValueOnce('weird');
    await expect(svc.verifyPassword('pw', 'h')).resolves.toBe(false);
  });

  test('verifyAgainstDummy returns argon2 result when it does not throw', async () => {
    verify.mockResolvedValueOnce(false);
    await expect(svc.verifyAgainstDummy('pw')).resolves.toBe(false);
    expect(verify).toHaveBeenCalledWith(await svc.getDummyHash(), 'pw');
  });

  test('verifyAgainstDummy returns false when argon2 throws', async () => {
    verify.mockRejectedValueOnce(new Error('invalid hash'));
    await expect(svc.verifyAgainstDummy('pw')).resolves.toBe(false);
  });
});
