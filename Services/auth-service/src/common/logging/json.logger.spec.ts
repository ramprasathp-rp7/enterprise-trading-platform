import { JsonLogger } from './json.logger';

describe('JsonLogger', () => {
  const originalEnv = process.env.NODE_ENV;
  let logger: JsonLogger;
  let logSpy: jest.SpyInstance;
  let warnSpy: jest.SpyInstance;
  let errorSpy: jest.SpyInstance;
  let debugSpy: jest.SpyInstance;

  beforeEach(() => {
    logger = new JsonLogger('TestContext');
    logSpy = jest.spyOn(console, 'log').mockImplementation(() => undefined);
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    debugSpy = jest.spyOn(console, 'debug').mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
    process.env.NODE_ENV = originalEnv;
  });

  const parse = (spy: jest.SpyInstance) => JSON.parse(spy.mock.calls[0][0]);

  test('log writes an INFO entry with the default context', () => {
    logger.log('hello');
    const entry = parse(logSpy);
    expect(entry).toMatchObject({ level: 'INFO', context: 'TestContext', message: 'hello' });
    expect(new Date(entry.timestamp).toString()).not.toBe('Invalid Date');
  });

  test('log uses an explicit context when given', () => {
    logger.log('hello', 'Other');
    expect(parse(logSpy).context).toBe('Other');
  });

  test('defaults the context to App', () => {
    new JsonLogger().log('x');
    expect(parse(logSpy).context).toBe('App');
  });

  test('stringifies non-string messages', () => {
    logger.log({ a: 1 } as any);
    expect(parse(logSpy).message).toBe('{"a":1}');
  });

  test('warn writes a WARN entry', () => {
    logger.warn('careful');
    expect(parse(warnSpy)).toMatchObject({ level: 'WARN', message: 'careful' });
  });

  test('debug writes a DEBUG entry', () => {
    logger.debug('details');
    expect(parse(debugSpy)).toMatchObject({ level: 'DEBUG', message: 'details' });
  });

  test('verbose writes a VERBOSE entry to console.log', () => {
    logger.verbose('chatty');
    expect(parse(logSpy)).toMatchObject({ level: 'VERBOSE', message: 'chatty' });
  });

  describe('error', () => {
    test('omits trace when none is given', () => {
      logger.error('boom');
      const entry = parse(errorSpy);
      expect(entry).toMatchObject({ level: 'ERROR', context: 'TestContext', message: 'boom' });
      expect(entry.trace).toBeUndefined();
    });

    test('redacts trace outside development', () => {
      process.env.NODE_ENV = 'production';
      logger.error('boom', 'stack here');
      expect(parse(errorSpy).trace).toBe('[REDACTED]');
    });

    test('includes trace in development', () => {
      process.env.NODE_ENV = 'Development';
      logger.error('boom', 'stack here', 'Ctx');
      expect(parse(errorSpy)).toMatchObject({ trace: 'stack here', context: 'Ctx' });
    });

    test('redacts trace when NODE_ENV is unset', () => {
      delete process.env.NODE_ENV;
      logger.error('boom', 'stack');
      expect(parse(errorSpy).trace).toBe('[REDACTED]');
    });

    test('stringifies non-string messages', () => {
      logger.error({ code: 1 } as any);
      expect(parse(errorSpy).message).toBe('{"code":1}');
    });
  });

  describe('redactSensitive', () => {
    const redact = (value: any) => (logger as any).redactSensitive(value);

    test('returns primitives and null unchanged', () => {
      expect(redact('x')).toBe('x');
      expect(redact(5)).toBe(5);
      expect(redact(null)).toBeNull();
    });

    test('redacts sensitive keys case-insensitively and recurses into nested objects', () => {
      const input = {
        username: 'alice',
        Password: 'p',
        accessToken: 't',
        nested: { jwtValue: 'j', clientSecret: 's', ok: 1 },
      };
      expect(redact(input)).toEqual({
        username: 'alice',
        Password: '[REDACTED]',
        accessToken: '[REDACTED]',
        nested: { jwtValue: '[REDACTED]', clientSecret: '[REDACTED]', ok: 1 },
      });
      expect(input.Password).toBe('p');
    });

    test('handles arrays', () => {
      expect(redact([{ password: 'p' }, 'plain'])).toEqual([{ password: '[REDACTED]' }, 'plain']);
    });
  });
});
