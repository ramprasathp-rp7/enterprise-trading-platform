import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { TlsOptions } from 'node:tls';
import { Module } from '@nestjs/common';
import { TypeOrmModule, TypeOrmModuleOptions } from '@nestjs/typeorm';
import { ConfigurationService } from '../config/configuration.service';
import { User } from './entities/user.entity';
import { RefreshToken } from './entities/refresh-token.entity';
import { KnownAccountId } from './entities/known-account-id.entity';
import { UsersRepository } from './repositories/users.repository';
import { RefreshTokenRepository } from './repositories/refresh-token.repository';
import { KnownAccountIdsRepository } from './repositories/known-account-ids.repository';

/**
 * Database TLS from PGSSLMODE: unset/disable = plain, no-verify = encrypted only,
 * anything else = certificate checked against PGSSLROOTCERT (default Config/rds-ca-bundle.pem).
 */
export function databaseSsl(env: NodeJS.ProcessEnv = process.env): false | TlsOptions {
  const mode = env.PGSSLMODE || 'disable';
  if (mode === 'disable') return false;
  if (mode === 'no-verify') return { rejectUnauthorized: false };
  const caPath = env.PGSSLROOTCERT || resolve(process.cwd(), '..', '..', 'Config', 'rds-ca-bundle.pem');
  return { ca: readFileSync(caPath, 'utf8'), rejectUnauthorized: true };
}

/** TypeORM connection options, built from the service configuration */
export function typeOrmOptions(
  configService: ConfigurationService,
): TypeOrmModuleOptions {
  const dbConfig = configService.getDatabaseConfig();

  return {
    type: 'postgres',
    host: dbConfig.host,
    port: dbConfig.port,
    username: dbConfig.username,
    password: dbConfig.password,
    database: dbConfig.database,
    ssl: databaseSsl(),
    entities: [User, RefreshToken, KnownAccountId],
    synchronize: false,
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
  providers: [
    UsersRepository,
    RefreshTokenRepository,
    KnownAccountIdsRepository,
  ],
  exports: [
    UsersRepository,
    RefreshTokenRepository,
    KnownAccountIdsRepository,
  ],
})
export class DatabaseModule {}
