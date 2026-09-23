import {
  Entity,
  PrimaryColumn,
  CreateDateColumn,
} from 'typeorm';

@Entity('known_account_ids')
export class KnownAccountId {
  @PrimaryColumn({
    type: 'bigint',
  })
  account_id: number;

  @CreateDateColumn({
    type: 'timestamptz',
  })
  created_on: Date;
}
