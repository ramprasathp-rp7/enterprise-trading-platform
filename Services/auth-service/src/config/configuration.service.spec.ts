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
  ];

  beforeEach(() => {
    jest.spyOn(console, 'log').mockImplementation(() => undefined);
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    for (const key of managedKeys) delete process.env[key];
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

  test('applies defaults when only required variables are set', () => {
    process.env.JWT_SECRET = validSecret;
    process.env.PGPASSWORD = 'pw';
    const config = new ConfigurationService();

    expect(config.getJwtSecret()).toBe(validSecret);
    expect(config.getJwtIssuer()).toBe('auth-service');
    expect(config.getAccessTokenLifetime()).toBe(900);
    expect(config.getRefreshTokenLifetime()).toBe(604800);
    expect(config.getPort()).toBe(3000);
    expect(config.getDatabaseConfig()).toEqual({
      host: 'localhost',
      port: 5432,
      username: 'postgres',
      password: 'pw',
      database: 'enterprise_trading_platform',
      poolMax: 10,
    });
  });

  test('reads every value from the environment', () => {
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
  });
});
