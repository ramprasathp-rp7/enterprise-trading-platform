import { BadRequestException, HttpStatus, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';
import { AuthExceptionFilter } from './common/errors/auth-exception.filter';
import { JsonLogger } from './common/logging/json.logger';
import { bootstrap, configureApp, validationExceptionFactory } from './main';

const config = {
  getCorsOrigins: () => ['http://localhost:4200'],
  getPort: jest.fn(() => 3000),
};

const fakeApp = () => ({
  get: jest.fn(() => config),
  enableCors: jest.fn(),
  useGlobalFilters: jest.fn(),
  useGlobalPipes: jest.fn(),
  listen: jest.fn().mockResolvedValue(undefined),
});

describe('main', () => {
  beforeEach(() => {
    jest.spyOn(SwaggerModule, 'createDocument').mockReturnValue({} as any);
    jest.spyOn(SwaggerModule, 'setup').mockImplementation(() => undefined);
    jest.spyOn(JsonLogger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(JsonLogger.prototype, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
    config.getPort.mockReturnValue(3000);
  });

  describe('validationExceptionFactory', () => {
    test('joins every constraint into one 422 message', () => {
      const exception = validationExceptionFactory([
        { property: 'username', constraints: { isNotEmpty: 'username is required', length: 'username is too long' } },
        { property: 'password', constraints: { minLength: 'password is too short' } },
      ] as any);

      expect(exception).toBeInstanceOf(BadRequestException);
      expect(exception.getStatus()).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
      expect(exception.getResponse()).toEqual({
        statusCode: HttpStatus.UNPROCESSABLE_ENTITY,
        message: 'username is required, username is too long; password is too short',
        error: 'Unprocessable Entity',
      });
    });

    test('falls back to the property name, then to a generic message', () => {
      expect((validationExceptionFactory([{ property: 'role' }] as any).getResponse() as any).message).toBe('role');
      expect((validationExceptionFactory([]).getResponse() as any).message).toBe('Validation failed');
    });
  });

  test('configureApp sets up CORS from the configuration, the error filter, validation and the docs', () => {
    const app = fakeApp();

    configureApp(app as any);

    expect(app.enableCors).toHaveBeenCalledWith(expect.objectContaining({
      origin: ['http://localhost:4200'],
      credentials: true,
    }));
    expect(app.useGlobalFilters.mock.calls[0][0]).toBeInstanceOf(AuthExceptionFilter);
    expect(app.useGlobalPipes.mock.calls[0][0]).toBeInstanceOf(ValidationPipe);
    expect(SwaggerModule.setup).toHaveBeenCalledWith('docs', app, {}, { jsonDocumentUrl: 'docs/json' });
  });

  test('bootstrap listens on the configured port', async () => {
    const app = fakeApp();
    jest.spyOn(NestFactory, 'create').mockResolvedValue(app as any);

    await bootstrap();
    expect(app.listen).toHaveBeenCalledWith(3000);

    config.getPort.mockReturnValue(3100);
    await bootstrap();
    expect(app.listen).toHaveBeenLastCalledWith(3100);
  });

  test('bootstrap logs a redacted error and exits when the app cannot start', async () => {
    // npm test loads the repository .env, which may set NODE_ENV=development (full stacks)
    const env = process.env.NODE_ENV;
    process.env.NODE_ENV = 'production';
    try {
      const exit = jest.spyOn(process, 'exit').mockImplementation((() => undefined) as any);
      jest.spyOn(NestFactory, 'create').mockRejectedValue(new Error('db down'));

      await bootstrap();

      expect(JsonLogger.prototype.error).toHaveBeenCalledWith('Failed to start application', '[REDACTED]');
      expect(exit).toHaveBeenCalledWith(1);
    } finally {
      process.env.NODE_ENV = env;
    }
  });

  test('bootstrap shows the stack in development and stringifies non-errors', async () => {
    const env = process.env.NODE_ENV;
    jest.spyOn(process, 'exit').mockImplementation((() => undefined) as any);
    const create = jest.spyOn(NestFactory, 'create');
    try {
      process.env.NODE_ENV = 'development';
      const error = new Error('port in use');
      create.mockRejectedValueOnce(error);
      await bootstrap();
      expect(JsonLogger.prototype.error).toHaveBeenLastCalledWith('Failed to start application', error.stack);

      create.mockRejectedValueOnce('boom');
      await bootstrap();
      expect(JsonLogger.prototype.error).toHaveBeenLastCalledWith('Failed to start application', 'boom');
    } finally {
      process.env.NODE_ENV = env;
    }
  });
});
