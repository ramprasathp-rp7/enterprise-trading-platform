import { Module, Global } from '@nestjs/common';
import { ThrottlerModule } from '@nestjs/throttler';
import { ConfigurationService } from '../config/configuration.service';
import { DatabaseModule } from '../database/database.module';
import { UsersModule } from '../users/users.module';
import { TokensModule } from '../tokens/tokens.module';
import { AuthModule } from '../auth/auth.module';

@Global()
@Module({
  imports: [
    ThrottlerModule.forRoot([
      {
        ttl: 60000, // 60 seconds
        limit: 5, // 5 attempts per ttl
      },
    ]),
    DatabaseModule,
    UsersModule,
    TokensModule,
    AuthModule,
  ],
  providers: [ConfigurationService],
  exports: [ConfigurationService, DatabaseModule, UsersModule, TokensModule, AuthModule],
})
export class AppModule {}
