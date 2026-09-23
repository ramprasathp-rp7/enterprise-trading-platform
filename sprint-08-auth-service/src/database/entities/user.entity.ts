import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('users')
@Index('uq_users_username', ['username'], { unique: true })
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({
    type: 'varchar',
    length: 64,
    unique: true,
  })
  username: string;

  @Column({
    type: 'varchar',
    length: 255,
  })
  password_hash: string;

  @Column({
    type: 'bigint',
  })
  account_id: number;

  @Column({
    type: 'text',
    array: true,
    default: () => `ARRAY['CUSTOMER']`,
  })
  roles: string[];

  @CreateDateColumn({
    type: 'timestamptz',
  })
  created_on: Date;

  @UpdateDateColumn({
    type: 'timestamptz',
  })
  last_updated: Date;
}
