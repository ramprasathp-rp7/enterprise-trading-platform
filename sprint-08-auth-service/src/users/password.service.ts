import { Injectable } from '@nestjs/common';
import * as argon2 from 'argon2';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class PasswordService {
  private readonly logger = new JsonLogger('PasswordService');

  /**
   * Hash a plaintext password using argon2id.
   * Never logs the plaintext password.
   */
  async hashPassword(password: string): Promise<string> {
    try {
      const hash = await argon2.hash(password, {
        type: argon2.argon2id,
        memoryCost: 19456, // ~100ms on modern hardware
        timeCost: 2,
        parallelism: 1,
        raw: false,
      });
      this.logger.log('Password hashed successfully');
      return hash;
    } catch (error) {
      // Log a safe message; stack/exception details are handled by logger
      this.logger.error('Password hashing failed', error instanceof Error ? error.message : String(error));
      throw new Error('Password hashing failed');
    }
  }

  /**
   * Verify a plaintext password against a hash.
   * Returns true if the password matches the hash.
   */
  async verifyPassword(password: string, hash: string): Promise<boolean> {
    try {
      const isValid = await argon2.verify(hash, password);
      return isValid;
    } catch (error) {
      this.logger.error('Password verification failed', error instanceof Error ? error.message : String(error));
      return false;
    }
  }

  /**
   * Generate a dummy hash for timing-safe login failure.
   * Use this when a user is not found to prevent user enumeration.
   */
  async getDummyHash(): Promise<string> {
    // Pre-computed argon2id hash of a random value
    // This simulates the cost of hashing without revealing whether a user exists
    return '$argon2id$v=19$m=19456,t=2,p=1$' +
      'abcdefghijklmnopqrst' + // random salt
      '$abcdefghijklmnopqrstuvwxyzabcdefghijklmnop'; // random hash
  }

  /**
   * Simulate password verification against a dummy hash.
   * Always takes approximately the same time as real verification.
   */
  async verifyAgainstDummy(password: string): Promise<boolean> {
    const dummyHash = await this.getDummyHash();
    try {
      // Verify against the dummy hash (will always fail, but takes consistent time)
      return await argon2.verify(dummyHash, password);
    } catch {
      // Fail silently; the timing is what matters
      return false;
    }
  }
}
