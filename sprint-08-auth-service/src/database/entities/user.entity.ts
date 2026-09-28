import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  ValueTransformer,
} from 'typeorm';
import { Role } from '../../common/roles.enum';

/**
 * Transformer to convert BIGINT from database (which TypeORM may return as string)
 * to JavaScript number type. This ensures account_id is always a number.
 */
class BigIntToNumberTransformer implements ValueTransformer {
  to(value: number | null): number | null {
    return value;
  }

  from(value: any): number {
    return Number(value);
  }
}

@Entity('users', { schema: 'auth' })
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
    transformer: new BigIntToNumberTransformer(),
  })
  account_id: number;

  @Column({
    type: 'text',
    array: true,
    default: () => `ARRAY['${Role.CUSTOMER}']`,
  })
  roles: Role[];

  @CreateDateColumn({
    type: 'timestamptz',
  })
  created_on: Date;

  @UpdateDateColumn({
    type: 'timestamptz',
  })
  last_updated: Date;
}
