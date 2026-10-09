import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { ValidationPipe, BadRequestException, HttpStatus, INestApplication, ValidationError } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app/app.module';
import { JsonLogger } from './common/logging/json.logger';
import { AuthExceptionFilter } from './common/errors/auth-exception.filter';
import { ConfigurationService } from './config/configuration.service';

/**
 * Turns class-validator errors into a 422 VAL-422 response (the filter maps the status)
 */
export function validationExceptionFactory(errors: ValidationError[]): BadRequestException {
  const messages = errors
    .map((error) => {
      if (error.constraints) {
        return Object.values(error.constraints).join(', ');
      }
      return error.property;
    })
    .join('; ');

  // Return a BadRequestException that will be caught by the filter
  // The filter will convert this to 422 VAL-422
  const exception = new BadRequestException({
    statusCode: HttpStatus.UNPROCESSABLE_ENTITY,
    message: messages || 'Validation failed',
    error: 'Unprocessable Entity',
  });
  // Manually set status to trigger VAL-422 handling in filter
  (exception as any).status = HttpStatus.UNPROCESSABLE_ENTITY;
  return exception;
}

/** CORS, the exception filter, validation and the Swagger docs */
export function configureApp(app: INestApplication): void {
  const configService = app.get(ConfigurationService);

  // Enable CORS for browser requests from UI
  app.enableCors({
    origin: configService.getCorsOrigins(),
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Accept'],
  });

  // Global exception filter (must be before pipes)
  app.useGlobalFilters(new AuthExceptionFilter());

  // Global validation pipe with custom exception factory for VAL-422
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      exceptionFactory: validationExceptionFactory,
    }),
  );

  // Swagger/OpenAPI
  const config = new DocumentBuilder()
    .setTitle('Auth Service')
    .setDescription('Authentication service for the Enterprise Trading Platform')
    .setVersion('1.0.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('docs', app, document, {
    jsonDocumentUrl: 'docs/json',
  });
}

export async function bootstrap(): Promise<void> {
  const logger = new JsonLogger('Bootstrap');

  try {
    const app = await NestFactory.create(AppModule, {
      logger: new JsonLogger('NestFactory'),
    });
    configureApp(app);

    const configService = app.get(ConfigurationService);

    const port = configService.getPort();
    await app.listen(port);
    logger.log(`Auth service listening on port ${port}`);
  } catch (error: unknown) {
    // Avoid leaking exception stacks in production logs; log redacted trace.
    const traceOrMsg = error instanceof Error ? (process.env.NODE_ENV === 'development' ? error.stack : '[REDACTED]') : String(error);
    logger.error('Failed to start application', traceOrMsg);
    process.exit(1);
  }
}

// Started with `node dist/main.js`; importing the file (tests) doesn't start a server
if (require.main === module) {
  void bootstrap();
}
