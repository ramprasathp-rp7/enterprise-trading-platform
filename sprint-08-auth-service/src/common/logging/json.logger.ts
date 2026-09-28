import { LoggerService } from '@nestjs/common';

interface LogObject {
  timestamp: string;
  level: string;
  context: string;
  message: string;
  [key: string]: any;
}

export class JsonLogger implements LoggerService {
  constructor(private defaultContext: string = 'App') {}

  private redactSensitive(obj: any): any {
    if (typeof obj !== 'object' || obj === null) {
      return obj;
    }

    const sensitiveKeys = ['password', 'token', 'refreshToken', 'jwt', 'secret'];
    const redacted = Array.isArray(obj) ? [...obj] : { ...obj };

    for (const key in redacted) {
      if (sensitiveKeys.some(sk => key.toLowerCase().includes(sk))) {
        redacted[key] = '[REDACTED]';
      } else if (typeof redacted[key] === 'object' && redacted[key] !== null) {
        redacted[key] = this.redactSensitive(redacted[key]);
      }
    }

    return redacted;
  }

  private formatMessage(level: string, message: string, context: string = this.defaultContext): string {
    // Handle case where message is not a string
    const msg = typeof message === 'string' ? message : JSON.stringify(message);
    
    const logObject: LogObject = {
      timestamp: new Date().toISOString(),
      level,
      context,
      message: msg,
    };

    return JSON.stringify(logObject);
  }

  log(message: string, context?: string): void {
    console.log(this.formatMessage('INFO', message, context));
  }

  error(message: string, trace?: string, context?: string): void {
    const ctx = context || this.defaultContext;
    const logObject: LogObject = {
      timestamp: new Date().toISOString(),
      level: 'ERROR',
      context: ctx,
      message: typeof message === 'string' ? message : JSON.stringify(message),
    };
    // Avoid emitting full stack traces in production logs. Only include trace
    // when running in development mode to prevent leaking exception names/details.
    const isDev = (process.env.NODE_ENV || '').toLowerCase() === 'development';
    if (trace && isDev) {
      logObject.trace = trace;
    } else if (trace) {
      logObject.trace = '[REDACTED]';
    }
    console.error(JSON.stringify(logObject));
  }

  warn(message: string, context?: string): void {
    console.warn(this.formatMessage('WARN', message, context));
  }

  debug(message: string, context?: string): void {
    console.debug(this.formatMessage('DEBUG', message, context));
  }

  verbose(message: string, context?: string): void {
    console.log(this.formatMessage('VERBOSE', message, context));
  }
}
