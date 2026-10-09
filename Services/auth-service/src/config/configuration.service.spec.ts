import { mkdtempSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { ConfigurationService } from './configuration.service';

describe('ConfigurationService', () => {
  const originalEnv = { ...process.env };
  const validSecret = 'x'.repeat(32);

  const managedKeys = [
    'JWT_SECRET',
    'JWT_ISSUER',
    'PGHOST',
    'PGPORT',
    'PGUSER',
    'PGPASSWORD',
    'AUTH_POSTGRES_DB',
    'ACCESS_TOKEN_LIFETIME',
    'REFRESH_TOKEN_LIFETIME',
    'PORT',
    'PGPOOL_MAX',
    'PLATFORM_CONFIG_PATH',
  ];

  beforeEach(() => {
    jest.spyOn(console, 'log').mockImplementation(() => undefined);
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    for (const key of managedKeys) delete process.env[key];
    // Keep tests independent of the repo's Config/application.yaml
    process.env.PLATFORM_CONFIG_PATH = join(tmpdir(), 'missing-platform-config.yaml');
  });

  afterEach(() => {
    jest.restoreAllMocks();
    process.env = { ...originalEnv };
  });

  test('throws when JWT_SECRET is missing', () => {
    process.env.PGPASSWORD = 'pw';
    expect(() => new ConfigurationService()).toThrow('JWT_SECRET environment variable is required');
  });

  test('throws when JWT_SECRET is shorter than 32 bytes', () => {
    process.env.JWT_SECRET = 'short';
    process.env.PGPASSWORD = 'pw';
    expect(() => new ConfigurationService()).toThrow('JWT_SECRET must be at least 32 bytes');
  });

  test('throws when PGPASSWORD is missing', () => {
    process.env.JWT_SECRET = validSecret;
    expect(() => new ConfigurationService()).toThrow('PGPASSWORD environment variable is required');
  });

  test('refuses to start when a host, port or name is configured nowhere', () => {
    process.env.JWT_SECRET = validSecret;
    process.env.PGPASSWORD = 'pw';
    process.env.JWT_ISSUER = 'auth-service';

    // No env var and no config file: no silent localhost
    expect(() => new ConfigurationService()).toThrow('PGHOST / database.host is not configured');
  });

  test('requires JWT_ISSUER', () => {
    Object.assign(process.env, { JWT_SECRET: validSecret, PGPASSWORD: 'pw', PGHOST: 'db', PGPORT: '5432', PGUSER: 'u', AUTH_POSTGRES_DB: 'd' });

    expect(() => new ConfigurationService()).toThrow('JWT_ISSUER is not configured');
  });

  describe('with a platform config file', () => {
    let dir: string;

    beforeEach(() => {
      dir = mkdtempSync(join(tmpdir(), 'platform-config-'));
      process.env.PLATFORM_CONFIG_PATH = join(dir, 'application.yaml');
      writeFileSync(
        process.env.PLATFORM_CONFIG_PATH,
        [
          'frontend:',
          '  url: http://ui.local',
          '  port: 4300',
          'services:',
          '  auth:',
          '    url: http://auth.local',
          '    port: 3100',
          'database:',
          '  host: db.local',
          '  port: 5433',
          '  name: yaml_db',
          '  user: yaml_user',
        ].join('\n'),
      );
      process.env.JWT_SECRET = validSecret;
      process.env.PGPASSWORD = 'pw';
      process.env.JWT_ISSUER = 'auth-service';
    });

    afterEach(() => rmSync(dir, { recursive: true, force: true }));

    test('tuning values default when not set', () => {
      const config = new ConfigurationService();

      expect(config.getJwtIssuer()).toBe('auth-service');
      expect(config.getAccessTokenLifetime()).toBe(900);
      expect(config.getRefreshTokenLifetime()).toBe(604800);
      expect(config.getDatabaseConfig().poolMax).toBe(10);
    });

    test('needs the frontend and auth entries for the CORS origins', () => {
      writeFileSync(
        process.env.PLATFORM_CONFIG_PATH!,
        ['database:', '  host: db', '  port: 5432', '  name: d', '  user: u', 'services:', '  auth:', '    port: 3000'].join('\n'),
      );

      expect(() => new ConfigurationService()).toThrow('frontend.url / frontend.port is not configured');
    });

    test('reads ports, origins and database settings from the file', () => {
      const config = new ConfigurationService();

      expect(config.getPort()).toBe(3100);
      expect(config.getCorsOrigins()).toEqual(['http://ui.local:4300', 'http://auth.local:3100']);
      expect(config.getDatabaseConfig()).toMatchObject({
        host: 'db.local',
        port: 5433,
        username: 'yaml_user',
        database: 'yaml_db',
      });
    });

    test('lets environment variables override the file', () => {
      process.env.PORT = '4000';
      process.env.AUTH_POSTGRES_DB = 'env_db';
      const config = new ConfigurationService();

      expect(config.getPort()).toBe(4000);
      expect(config.getDatabaseConfig().database).toBe('env_db');
    });
  });

  test('reads every value from the environment (origins still from the config file)', () => {
    const dir = mkdtempSync(join(tmpdir(), 'platform-config-'));
    process.env.PLATFORM_CONFIG_PATH = join(dir, 'application.yaml');
    writeFileSync(
      process.env.PLATFORM_CONFIG_PATH,
      ['frontend:', '  url: http://ui', '  port: 4200', 'services:', '  auth:', '    url: http://auth', '    port: 3000'].join('\n'),
    );
    Object.assign(process.env, {
      JWT_SECRET: validSecret,
      JWT_ISSUER: 'issuer-x',
      PGHOST: 'db',
      PGPORT: '6543',
      PGUSER: 'auth',
      PGPASSWORD: 'secret',
      AUTH_POSTGRES_DB: 'authdb',
      ACCESS_TOKEN_LIFETIME: '60',
      REFRESH_TOKEN_LIFETIME: '120',
      PORT: '4000',
      PGPOOL_MAX: '3',
    });
    const config = new ConfigurationService();

    expect(config.getJwtIssuer()).toBe('issuer-x');
    expect(config.getAccessTokenLifetime()).toBe(60);
    expect(config.getRefreshTokenLifetime()).toBe(120);
    expect(config.getPort()).toBe(4000);
    expect(config.getDatabaseConfig()).toEqual({
      host: 'db',
      port: 6543,
      username: 'auth',
      password: 'secret',
      database: 'authdb',
      poolMax: 3,
    });
    expect(config.get('jwt').issuer).toBe('issuer-x');
    expect(config.get('missing')).toBeUndefined();
    rmSync(dir, { recursive: true, force: true });
  });
});
