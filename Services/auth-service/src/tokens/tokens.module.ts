import { Module } from '@nestjs/common';
import { JwtModule, JwtModuleOptions } from '@nestjs/jwt';
import { ConfigurationService } from '../config/configuration.service';
import { DatabaseModule } from '../database/database.module';
import { TokenService } from './tokens.service';

/** JWT signing options, built from the service configuration */
export function jwtOptions(configService: ConfigurationService): JwtModuleOptions {
  return {
    secret: configService.getJwtSecret(),
    signOptions: {
      issuer: configService.getJwtIssuer(),
      expiresIn: configService.getAccessTokenLifetime(),
    },
  };
}

@Module({
  imports: [
    JwtModule.registerAsync({
      inject: [ConfigurationService],
      useFactory: jwtOptions,
    }),
    DatabaseModule,
  ],
  providers: [TokenService],
  exports: [TokenService, JwtModule],
})
export class TokensModule {}
