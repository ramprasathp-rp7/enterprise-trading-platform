import {
  Entity,
  PrimaryColumn,
  Column,
  CreateDateColumn,
} from 'typeorm';

@Entity('known_account_ids', { schema: 'auth' })
export class KnownAccountId {
  @PrimaryColumn({
    type: 'bigint',
  })
  id: number;

  @Column({
    type: 'varchar',
    length: 32,
    unique: true,
  })
  account_id: string;

  @CreateDateColumn({
    type: 'timestamptz',
  })
  created_on: Date;
}
