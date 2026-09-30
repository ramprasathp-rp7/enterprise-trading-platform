import { Injectable } from '@nestjs/common';
import { JsonLogger } from '../common/logging/json.logger';

@Injectable()
export class ConfigurationService {
  private readonly logger = new JsonLogger('ConfigurationService');

  private readonly config: Record<string, any>;

  constructor() {
    this.config = this.validateEnv();
  }

  private validateEnv(): Record<string, any> {
    const logger = this.logger;

    // JWT_SECRET validation
    const jwtSecret = process.env.JWT_SECRET;
    if (!jwtSecret) {
      logger.error('JWT_SECRET is not set');
      throw new Error('JWT_SECRET environment variable is required');
    }
    if (jwtSecret.length < 32) {
      logger.error(`JWT_SECRET is too short: ${jwtSecret.length} bytes (minimum 32 required)`);
      throw new Error('JWT_SECRET must be at least 32 bytes');
    }

    // Database configuration
    const pgHost = process.env.PGHOST || 'localhost';
    const pgPort = parseInt(process.env.PGPORT || '5432', 10);
    const pgUser = process.env.PGUSER || 'postgres';
    const pgPassword = process.env.PGPASSWORD;
    const pgDatabase = process.env.AUTH_POSTGRES_DB || 'enterprise_trading_platform';

    if (!pgPassword) {
      logger.error('PGPASSWORD is not set');
      throw new Error('PGPASSWORD environment variable is required');
    }

    // Other configuration
    const jwtIssuer = process.env.JWT_ISSUER || 'auth-service';
    const accessTokenLifetime = parseInt(process.env.ACCESS_TOKEN_LIFETIME || '900', 10);
    const refreshTokenLifetime = parseInt(process.env.REFRESH_TOKEN_LIFETIME || '604800', 10);
    const port = parseInt(process.env.PORT || '3000', 10);
    const pgPoolMax = parseInt(process.env.PGPOOL_MAX || '10', 10);

    const config = {
      jwt: {
        secret: jwtSecret,
        issuer: jwtIssuer,
        accessTokenLifetime,
        refreshTokenLifetime,
      },
      database: {
        host: pgHost,
        port: pgPort,
        username: pgUser,
        password: pgPassword,
        database: pgDatabase,
        poolMax: pgPoolMax,
      },
      port,
    };

    logger.log(`Configuration loaded and validated | port=${port} jwtIssuer=${jwtIssuer} pgHost=${pgHost} pgDatabase=${pgDatabase}`);
    return config;
  }

  get(key: string): any {
    return this.config[key];
  }

  getJwtSecret(): string {
    return this.config.jwt.secret;
  }

  getJwtIssuer(): string {
    return this.config.jwt.issuer;
  }

  getAccessTokenLifetime(): number {
    return this.config.jwt.accessTokenLifetime;
  }

  getRefreshTokenLifetime(): number {
    return this.config.jwt.refreshTokenLifetime;
  }

  getDatabaseConfig(): Record<string, any> {
    return this.config.database;
  }

  getPort(): number {
    return this.config.port;
  }

}
