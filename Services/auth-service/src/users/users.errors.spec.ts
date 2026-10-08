import {
  AccountIdAlreadyExistsError,
  InvalidPasswordError,
  UserNotFoundError,
  UsernameAlreadyExistsError,
} from './users.errors';

describe('users errors', () => {
  test.each([
    [new UsernameAlreadyExistsError('alice'), 'UsernameAlreadyExistsError', "Username 'alice' is already taken"],
    [new AccountIdAlreadyExistsError(7), 'AccountIdAlreadyExistsError', "Account ID '7' is already associated with a user"],
    [new UserNotFoundError('u1'), 'UserNotFoundError', 'User not found: u1'],
    [new InvalidPasswordError(), 'InvalidPasswordError', 'Invalid password'],
  ])('%s has the right name and message', (error, name, message) => {
    expect(error).toBeInstanceOf(Error);
    expect(error.name).toBe(name);
    expect(error.message).toBe(message);
  });
});
