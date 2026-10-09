import { Module } from '@nestjs/common';
import { TypeOrmModule, TypeOrmModuleOptions } from '@nestjs/typeorm';
import { ConfigurationService } from '../config/configuration.service';
import { User } from './entities/user.entity';
import { RefreshToken } from './entities/refresh-token.entity';
import { KnownAccountId } from './entities/known-account-id.entity';
import { UsersRepository } from './repositories/users.repository';
import { RefreshTokenRepository } from './repositories/refresh-token.repository';
import { KnownAccountIdsRepository } from './repositories/known-account-ids.repository';

/** TypeORM connection options, built from the service configuration */
export function typeOrmOptions(configService: ConfigurationService): TypeOrmModuleOptions {
  const dbConfig = configService.getDatabaseConfig();
  return {
    type: 'postgres',
    host: dbConfig.host,
    port: dbConfig.port,
    username: dbConfig.username,
    password: dbConfig.password,
    database: dbConfig.database,
    entities: [User, RefreshToken, KnownAccountId],
    synchronize: false, // Auto-create tables in development
    logging: false,
    maxQueryExecutionTime: 1000,
    extra: {
      max: dbConfig.poolMax,
    },
  };
}

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      inject: [ConfigurationService],
      useFactory: typeOrmOptions,
    }),
    TypeOrmModule.forFeature([User, RefreshToken, KnownAccountId]),
  ],
  providers: [UsersRepository, RefreshTokenRepository, KnownAccountIdsRepository],
  exports: [UsersRepository, RefreshTokenRepository, KnownAccountIdsRepository],
})
export class DatabaseModule {}
