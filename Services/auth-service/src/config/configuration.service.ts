import { Injectable } from '@nestjs/common';
import { JsonLogger } from '../common/logging/json.logger';
import { loadPlatformConfig, toOrigin } from './platform-config';

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

    // Ports and database come from Config/application.yaml; env vars still override them
    const platform = loadPlatformConfig();

    // Database configuration
    // No hardcoded hosts, ports or names: each comes from an env var or Config/application.yaml
    const required = <T>(value: T | undefined | null | '', setting: string): T => {
      if (value === undefined || value === null || value === '') {
        logger.error(`${setting} is not configured`);
        throw new Error(`${setting} is not configured: set it in .env or Config/application.yaml`);
      }
      return value;
    };
    const pgPassword = process.env.PGPASSWORD;
    if (!pgPassword) {
      logger.error('PGPASSWORD is not set');
      throw new Error('PGPASSWORD environment variable is required');
    }
    const pgHost = required(process.env.PGHOST || platform.database?.host, 'PGHOST / database.host');
    const pgPort = parseInt(String(required(process.env.PGPORT || platform.database?.port, 'PGPORT / database.port')), 10);
    const pgUser = required(process.env.PGUSER || platform.database?.user, 'PGUSER / database.user');
    const pgDatabase = required(process.env.AUTH_POSTGRES_DB || platform.database?.name, 'AUTH_POSTGRES_DB / database.name');

    // Other configuration
    const jwtIssuer = required(process.env.JWT_ISSUER, 'JWT_ISSUER');
    const accessTokenLifetime = parseInt(process.env.ACCESS_TOKEN_LIFETIME || '900', 10);
    const refreshTokenLifetime = parseInt(process.env.REFRESH_TOKEN_LIFETIME || '604800', 10);
    const port = parseInt(String(required(process.env.PORT || platform.services?.auth?.port, 'PORT / services.auth.port')), 10);
    const corsOrigins = [
      required(toOrigin(platform.frontend), 'frontend.url / frontend.port'),
      required(toOrigin(platform.services?.auth), 'services.auth.url / services.auth.port'),
    ];
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
      corsOrigins,
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

  getCorsOrigins(): string[] {
    return this.config.corsOrigins;
  }

}
