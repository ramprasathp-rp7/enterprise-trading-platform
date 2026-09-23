import { ExceptionFilter, Catch, ArgumentsHost, HttpException, HttpStatus, BadRequestException } from '@nestjs/common';
import { Response } from 'express';
import { ErrorCode, ErrorResponse } from './error-codes';
import { JsonLogger } from '../logging/json.logger';

@Catch()
export class AuthExceptionFilter implements ExceptionFilter {
  private readonly logger = new JsonLogger('AuthExceptionFilter');

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let errorCode = 'INTERNAL_ERROR';
    let message = 'An unexpected error occurred';

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const exceptionResponse = exception.getResponse();

      // Handle validation errors (BadRequestException from ValidationPipe)
      if (status === HttpStatus.BAD_REQUEST && typeof exceptionResponse === 'object') {
        const resp = exceptionResponse as any;
        if (resp.message && Array.isArray(resp.message)) {
          // Validation error from ValidationPipe
          status = HttpStatus.UNPROCESSABLE_ENTITY;
          errorCode = ErrorCode.VAL_422;
          message = resp.message[0] || 'Validation failed';
        }
      }

      // Handle conflict exceptions
      if (status === HttpStatus.CONFLICT) {
        errorCode = ErrorCode.AUTH_409;
        message = exception.message || 'Conflict';
      }

      // Handle unauthorized exceptions
      if (status === HttpStatus.UNAUTHORIZED) {
        errorCode = ErrorCode.AUTH_401;
        message = 'Unauthorised';
      }

      // For other HTTP exceptions, try to extract message
      if (typeof exceptionResponse === 'object') {
        const resp = exceptionResponse as any;
        message = resp.message || exception.message || message;
      } else {
        message = exception.message || message;
      }
    } else if (exception instanceof Error) {
      // Handle custom errors
      const msg = exception.message;

      if (msg.includes('already taken') || msg.includes('already exists')) {
        status = HttpStatus.CONFLICT;
        errorCode = ErrorCode.AUTH_409;
        message = 'Registration failed';
      } else if (msg.includes('not found') || msg.includes('Unknown user')) {
        status = HttpStatus.UNAUTHORIZED;
        errorCode = ErrorCode.AUTH_401;
        message = 'Unauthorised';
      } else {
        status = HttpStatus.INTERNAL_SERVER_ERROR;
        message = 'An error occurred';
      }

      this.logger.error(`Unhandled exception: ${msg}`);
    }

    const errorResponse: ErrorResponse = {
      errorCode: errorCode as any,
      message,
    };

    response.status(status).json(errorResponse);
  }
}
