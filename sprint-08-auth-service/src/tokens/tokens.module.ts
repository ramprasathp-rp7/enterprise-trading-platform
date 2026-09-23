import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { ConfigurationService } from '../config/configuration.service';
import { DatabaseModule } from '../database/database.module';
import { TokenService } from './tokens.service';

@Module({
  imports: [
    JwtModule.registerAsync({
      inject: [ConfigurationService],
      useFactory: (configService: ConfigurationService) => ({
        secret: configService.getJwtSecret(),
        signOptions: {
          issuer: configService.getJwtIssuer(),
          expiresIn: configService.getAccessTokenLifetime(),
        },
      }),
    }),
    DatabaseModule,
  ],
  providers: [TokenService],
  exports: [TokenService, JwtModule],
})
export class TokensModule {}
