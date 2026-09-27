export class UsernameAlreadyExistsError extends Error {
  constructor(username: string) {
    super(`Username '${username}' is already taken`);
    this.name = 'UsernameAlreadyExistsError';
  }
}

export class AccountIdAlreadyExistsError extends Error {
  constructor(accountId: string) {
    super(`Account ID '${accountId}' is already associated with a user`);
    this.name = 'AccountIdAlreadyExistsError';
  }
}

export class UserNotFoundError extends Error {
  constructor(identifier: string) {
    super(`User not found: ${identifier}`);
    this.name = 'UserNotFoundError';
  }
}

export class InvalidPasswordError extends Error {
  constructor() {
    super('Invalid password');
    this.name = 'InvalidPasswordError';
  }
}
