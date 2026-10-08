import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { RegisterDto } from './register.dto';

describe('RegisterDto', () => {
  const valid = { username: 'alice.b_1', password: 'longenoughpassword', accountId: 3 };

  const errorsFor = async (plain: Record<string, unknown>) => {
    const dto = plainToInstance(RegisterDto, plain);
    return (await validate(dto)).map((e) => e.property);
  };

  test('accepts a valid payload', async () => {
    await expect(errorsFor(valid)).resolves.toEqual([]);
  });

  test('converts a numeric string accountId to a number', () => {
    expect(plainToInstance(RegisterDto, { ...valid, accountId: '42' }).accountId).toBe(42);
  });

  test('leaves a non-numeric accountId as is so validation rejects it', async () => {
    expect(plainToInstance(RegisterDto, { ...valid, accountId: 'abc' }).accountId).toBe('abc');
    await expect(errorsFor({ ...valid, accountId: 'abc' })).resolves.toEqual(['accountId']);
  });

  test.each([
    ['too short', 'ab'],
    ['invalid characters', 'alice smith'],
  ])('rejects a username that is %s', async (_case, username) => {
    await expect(errorsFor({ ...valid, username })).resolves.toEqual(['username']);
  });

  test('rejects a password under 12 characters', async () => {
    await expect(errorsFor({ ...valid, password: 'short' })).resolves.toEqual(['password']);
  });

  test('rejects unknown roles', async () => {
    await expect(errorsFor({ ...valid, roles: ['SUPERUSER'] })).resolves.toEqual(['roles']);
  });
});
