import { Module } from '@nestjs/common';
import { DatabaseModule } from '../database/database.module';
import { UsersService } from './users.service';
import { PasswordService } from './password.service';
import { DemoUserSeeder } from './demo-user.seeder';

@Module({
  imports: [DatabaseModule],
  providers: [UsersService, PasswordService, DemoUserSeeder],
  exports: [UsersService, PasswordService, DemoUserSeeder],
})
export class UsersModule {}
