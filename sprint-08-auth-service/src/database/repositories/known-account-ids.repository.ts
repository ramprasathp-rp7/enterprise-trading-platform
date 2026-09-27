import { Injectable } from '@nestjs/common';
import { DataSource, Repository } from 'typeorm';
import { KnownAccountId } from '../entities/known-account-id.entity';

@Injectable()
export class KnownAccountIdsRepository extends Repository<KnownAccountId> {
  constructor(private dataSource: DataSource) {
    super(KnownAccountId, dataSource.createEntityManager());
  }

  async findByExternalAccountId(accountId: string): Promise<KnownAccountId | null> {
    return this.findOne({
      where: { account_id: accountId },
    });
  }

  async accountIdExists(accountId: string): Promise<boolean> {
    const record = await this.findByExternalAccountId(accountId);
    return record !== null;
  }

  async addAccountId(id: number, accountId: string): Promise<KnownAccountId> {
    const record = this.create({ id, account_id: accountId });
    return this.save(record);
  }
}
