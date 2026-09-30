import { PasswordService } from './password.service';

describe('PasswordService', () => {
  let svc: PasswordService;

  beforeAll(() => {
    svc = new PasswordService();
  });

  test('correct password verifies', async () => {
    const plain = 'S3cr3tP@ssw0rd!';
    const hash = await svc.hashPassword(plain);
    expect(typeof hash).toBe('string');
    const ok = await svc.verifyPassword(plain, hash);
    expect(ok).toBe(true);
  });

  test('incorrect password fails verification', async () => {
    const plain = 'password1';
    const other = 'password2';
    const hash = await svc.hashPassword(plain);
    const ok = await svc.verifyPassword(other, hash);
    expect(ok).toBe(false);
  });

  test('dummy hash verification runs and returns false', async () => {
    const res = await svc.verifyAgainstDummy('whatever');
    expect(res).toBe(false);
  });
});
