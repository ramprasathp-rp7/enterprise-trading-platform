import { Injectable } from '@nestjs/common';
import { DataSource, Repository, IsNull } from 'typeorm';
import { RefreshToken } from '../entities/refresh-token.entity';

@Injectable()
export class RefreshTokenRepository extends Repository<RefreshToken> {
  constructor(private dataSource: DataSource) {
    super(RefreshToken, dataSource.createEntityManager());
  }

  async saveToken(token: Partial<RefreshToken>): Promise<RefreshToken> {
    const newToken = this.create(token);
    return this.save(newToken);
  }

  async findByHash(hash: string): Promise<RefreshToken | null> {
    return this.findOne({
      where: { token_hash: hash },
    });
  }

  async findActiveByUserId(userId: string): Promise<RefreshToken[]> {
    return this.find({
      where: {
        user_id: userId,
        revoked_on: IsNull(),
      },
    });
  }

  async markRevoked(id: string): Promise<void> {
    await this.update(id, {
      revoked_on: new Date(),
    });
  }

  async findByFamilyId(familyId: string): Promise<RefreshToken[]> {
    return this.find({
      where: { family_id: familyId },
    });
  }

  async findById(id: string): Promise<RefreshToken | null> {
    return this.findOne({
      where: { id },
    });
  }

  async revokeFamily(familyId: string): Promise<void> {
    await this.update(
      { family_id: familyId },
      { revoked_on: new Date() },
    );
  }

  async markReplacedBy(id: string, replacedByTokenId: string): Promise<void> {
    await this.update(id, {
      replaced_by: replacedByTokenId,
    });
  }
}
