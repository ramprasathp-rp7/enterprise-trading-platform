import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
  ForeignKey,
} from 'typeorm';
import { User } from './user.entity';

@Entity('refresh_tokens', { schema: 'auth' })
@Index('ix_refresh_tokens_user_id', ['user_id'])
@Index('ix_refresh_tokens_family_id', ['family_id'])
@Index('ix_refresh_tokens_expires_on', ['expires_on'], { where: 'revoked_on IS NULL' })
export class RefreshToken {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({
    type: 'uuid',
  })
  user_id: string;

  @ManyToOne(() => User, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'user_id' })
  user: User;

  @Column({
    type: 'varchar',
    length: 255,
    unique: true,
  })
  token_hash: string;

  @Column({
    type: 'uuid',
  })
  family_id: string;

  @CreateDateColumn({
    type: 'timestamptz',
  })
  issued_on: Date;

  @Column({
    type: 'timestamptz',
  })
  expires_on: Date;

  @Column({
    type: 'timestamptz',
    nullable: true,
  })
  revoked_on: Date | null;

  @Column({
    type: 'uuid',
    nullable: true,
  })
  replaced_by: string | null;
}
