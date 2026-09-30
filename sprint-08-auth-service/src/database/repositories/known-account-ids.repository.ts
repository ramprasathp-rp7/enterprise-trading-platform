import { Injectable } from '@nestjs/common';
import { DataSource, Repository } from 'typeorm';
import { KnownAccountId } from '../entities/known-account-id.entity';

@Injectable()
export class KnownAccountIdsRepository extends Repository<KnownAccountId> {
  constructor(private dataSource: DataSource) {
    super(KnownAccountId, dataSource.createEntityManager());
  }

  async accountIdExists(accountId: number): Promise<boolean> {
    const count = await this.count({
      where: { account_id: accountId },
    });
    return count > 0;
  }

  async addAccountId(accountId: number): Promise<KnownAccountId> {
    const record = this.create({account_id:accountId});
    return this.save(record);
  }
}
