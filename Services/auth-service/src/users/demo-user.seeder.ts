import { Injectable } from '@nestjs/common';
import { KnownAccountIdsRepository } from '../database/repositories/known-account-ids.repository';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class DemoUserSeeder {
  private readonly logger = new JsonLogger('DemoUserSeeder');

  constructor(private accountIdsRepository: KnownAccountIdsRepository) {}

  /**
   * Seed known account IDs from the trading database.
   * Called once on startup if needed.
   * In production, this would be synced from the Trade Database via migration or API.
   */
  async seedKnownAccountIds(): Promise<void> {
    // For demo/development: seed a few known account IDs
    // In production, these are mirrored from the trade database
    const demoAccounts = [1,2,3];

    for (const accountId of demoAccounts) {
      const exists = await this.accountIdsRepository.accountIdExists(accountId);
      if (!exists) {
        await this.accountIdsRepository.addAccountId(accountId);
        this.logger.log(`Seeded account ID: ${accountId}`);
      }
    }
  }
}
