import { Injectable } from '@nestjs/common';
import { User } from '../database/entities/user.entity';
import { Role } from '../common/roles.enum';
import { RegisterDto } from './dto/register.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { UsersRepository } from '../database/repositories/users.repository';
import { KnownAccountIdsRepository } from '../database/repositories/known-account-ids.repository';
import { PasswordService } from './password.service';
import { UsernameAlreadyExistsError, UserNotFoundError, InvalidPasswordError, AccountIdAlreadyExistsError, UnknownAccountIdError } from './users.errors';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class UsersService {
  private readonly logger = new JsonLogger('UsersService');

  constructor(
    private usersRepository: UsersRepository,
    private accountIdsRepository: KnownAccountIdsRepository,
    private passwordService: PasswordService,
  ) {}

  /**
   * Register a new user.
   * Validates that the username is unique and accountId exists.
   * Returns UserResponse (no tokens).
   */
  async register(registerDto: RegisterDto): Promise<UserResponseDto> {
    const { username, password, accountId, roles } = registerDto;

    this.logger.log(`Registration attempt for username: ${username}`);

    // Check if username already exists
    const existingUser = await this.usersRepository.findByUsername(username);
    if (existingUser) {
      this.logger.warn(`Username already exists: ${username}`);
      throw new UsernameAlreadyExistsError(username);
    }

    // Validate that accountId exists in known_account_ids
    const accountExists = await this.accountIdsRepository.accountIdExists(accountId);
    if (!accountExists) {
      this.logger.warn(`Account ID not found: ${accountId}`);
      throw new UnknownAccountIdError(accountId);
    }

    // Ensure no other user has this accountId
    const existingByAccount = await this.usersRepository.findOne({ where: { account_id: accountId } });
    if (existingByAccount) {
      this.logger.warn(`Account ID already registered: ${accountId}`);
      throw new AccountIdAlreadyExistsError(accountId);
    }

    // Hash the password
    const passwordHash = await this.passwordService.hashPassword(password);

    // Create the user
    // SECURITY: Always register as CUSTOMER. Admins are seeded in the database only.
    // Accepting self-declared roles is a privilege-escalation vulnerability.
    const newUser = await this.usersRepository.saveUser({
      username,
      password_hash: passwordHash,
      account_id: accountId,
      roles: [Role.CUSTOMER],
    });

    this.logger.log(`User registered successfully: ${username} (id: ${newUser.id})`);

    return this.userToResponse(newUser);
  }

  /**
   * Find a user by username (for login).
   */
  async findByUsername(username: string): Promise<User | null> {
    return this.usersRepository.findByUsername(username);
  }

  /**
   * Find a user by ID (for /auth/me).
   */
  async findById(id: string): Promise<User | null> {
    return this.usersRepository.findById(id);
  }

  /**
   * Verify a password against a user's stored hash.
   * Supports timing-safe failure for unknown users.
   */
  async verifyPassword(user: User | null, password: string): Promise<boolean> {
    if (!user) {
      // User not found: verify against a dummy hash for timing safety
      await this.passwordService.verifyAgainstDummy(password);
      return false;
    }

    // User found: verify the actual password
    const isValid = await this.passwordService.verifyPassword(password, user.password_hash);
    if (!isValid) {
      this.logger.warn(`Invalid password attempt for user: ${user.username}`);
    }
    return isValid;
  }

  /**
   * Convert a User entity to a UserResponseDto.
   */
  private userToResponse(user: User): UserResponseDto {
    return {
      id: user.id,
      username: user.username,
      accountId: user.account_id,
      roles: user.roles,
      createdOn: user.created_on,
    };
  }
}
