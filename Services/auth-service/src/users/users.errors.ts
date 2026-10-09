export class UsernameAlreadyExistsError extends Error {
  constructor(username: string) {
    super(`Username '${username}' is already taken`);
    this.name = 'UsernameAlreadyExistsError';
  }
}

export class AccountIdAlreadyExistsError extends Error {
  constructor(accountId: number) {
    super(`Account ID '${accountId}' is already associated with a user`);
    this.name = 'AccountIdAlreadyExistsError';
  }
}

/** The accountId is not a trading account the platform knows (auth.known_account_ids) */
export class UnknownAccountIdError extends Error {
  constructor(accountId: number) {
    super(`Account ID ${accountId} does not exist`);
    this.name = 'UnknownAccountIdError';
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
