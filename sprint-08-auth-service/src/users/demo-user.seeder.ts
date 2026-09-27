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
    const demoAccounts = [
      { id: 1, accountId: 'ACC-000001' },
      { id: 2, accountId: 'ACC-000002' },
      { id: 3, accountId: 'ACC-000003' },
    ];

    for (const account of demoAccounts) {
      const exists = await this.accountIdsRepository.accountIdExists(account.accountId);
      if (!exists) {
        await this.accountIdsRepository.addAccountId(account.id, account.accountId);
        this.logger.log(`Seeded account ID: ${account.accountId} -> ${account.id}`);
      }
    }
  }
}
