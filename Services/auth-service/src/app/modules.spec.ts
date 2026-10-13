import { MODULE_METADATA } from '@nestjs/common/constants';
import { getMetadataArgsStorage } from 'typeorm';
import { AppModule } from './app.module';
import { AuthModule } from '../auth/auth.module';
import { AuthController } from '../auth/auth.controller';
import { AuthService } from '../auth/auth.service';
import { ConfigurationService } from '../config/configuration.service';
import { DatabaseModule, typeOrmOptions } from '../database/database.module';
import { KnownAccountId } from '../database/entities/known-account-id.entity';
import { RefreshToken } from '../database/entities/refresh-token.entity';
import { User } from '../database/entities/user.entity';
import { TokensModule, jwtOptions } from '../tokens/tokens.module';
import { TokenService } from '../tokens/tokens.service';
import { UsersModule } from '../users/users.module';
import { UsersService } from '../users/users.service';

const metadata = (target: object, key: string) => Reflect.getMetadata(key, target) as unknown[];

const config = {
  getDatabaseConfig: () => ({ host: 'db', port: 5432, username: 'auth', password: 'pw', database: 'trading', poolMax: 7 }),
  getJwtSecret: () => 'secret',
  getJwtIssuer: () => 'auth-service',
  getAccessTokenLifetime: () => 900,
} as unknown as ConfigurationService;

describe('module wiring', () => {
  test('the app module brings in every feature module and shares the configuration', () => {
    expect(metadata(AppModule, MODULE_METADATA.IMPORTS)).toEqual(
      expect.arrayContaining([DatabaseModule, UsersModule, TokensModule, AuthModule]),
    );
    expect(metadata(AppModule, MODULE_METADATA.PROVIDERS)).toEqual([ConfigurationService]);
    expect(metadata(AppModule, MODULE_METADATA.EXPORTS)).toContain(ConfigurationService);
  });

  test('feature modules register their providers and controllers', () => {
    expect(metadata(AuthModule, MODULE_METADATA.CONTROLLERS)).toEqual([AuthController]);
    expect(metadata(AuthModule, MODULE_METADATA.PROVIDERS)).toEqual([AuthService]);
    expect(metadata(UsersModule, MODULE_METADATA.PROVIDERS)).toContain(UsersService);
    expect(metadata(TokensModule, MODULE_METADATA.PROVIDERS)).toEqual([TokenService]);
  });

  test('database options come from the configuration and never synchronise the schema', () => {
    // npm test loads the repository .env, which may ask for verified TLS
    const sslMode = process.env.PGSSLMODE;
    process.env.PGSSLMODE = 'disable';
    const options = typeOrmOptions(config);
    if (sslMode === undefined) delete process.env.PGSSLMODE;
    else process.env.PGSSLMODE = sslMode;

    expect(options).toEqual(expect.objectContaining({
      ssl: false,
      type: 'postgres',
      host: 'db',
      port: 5432,
      username: 'auth',
      password: 'pw',
      database: 'trading',
      entities: [User, RefreshToken, KnownAccountId],
      synchronize: false,
      extra: { max: 7 },
    }));
  });

  test('JWT options sign with the configured secret, issuer and lifetime', () => {
    expect(jwtOptions(config)).toEqual({
      secret: 'secret',
      signOptions: { issuer: 'auth-service', expiresIn: 900 },
    });
  });
});

describe('entity mappings', () => {
  const storage = getMetadataArgsStorage();

  test('account_id is read back as a number even when Postgres returns a string', () => {
    const column = storage.columns.find((c) => c.target === User && c.propertyName === 'account_id');
    const transformer = column?.options.transformer as { to(v: unknown): unknown; from(v: unknown): unknown };

    expect(transformer.from('42')).toBe(42);
    expect(transformer.to(42)).toBe(42);
    expect(transformer.to(null)).toBeNull();
  });

  test('new users default to the customer role', () => {
    const column = storage.columns.find((c) => c.target === User && c.propertyName === 'roles');
    const defaultValue = column?.options.default as () => string;

    expect(defaultValue()).toBe("ARRAY['CUSTOMER']");
  });

  test('a refresh token belongs to a user', () => {
    const relation = storage.relations.find((r) => r.target === RefreshToken && r.propertyName === 'user');
    const type = relation?.type as () => unknown;

    expect(type()).toBe(User);
  });
});
