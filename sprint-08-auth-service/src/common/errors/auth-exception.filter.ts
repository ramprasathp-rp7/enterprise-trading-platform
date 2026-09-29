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

      // Handle validation errors from ValidationPipe
      // The ValidationPipe may return a BadRequestException with an array
      // of messages, or the global exception factory may create a
      // BadRequestException whose status (or response.statusCode) is 422.
      if (
        (status === HttpStatus.BAD_REQUEST && typeof exceptionResponse === 'object' && Array.isArray((exceptionResponse as any).message)) ||
        status === HttpStatus.UNPROCESSABLE_ENTITY ||
        (typeof exceptionResponse === 'object' && (exceptionResponse as any).statusCode === HttpStatus.UNPROCESSABLE_ENTITY)
      ) {
        const resp = typeof exceptionResponse === 'object' ? (exceptionResponse as any) : undefined;
        errorCode = ErrorCode.VAL_422;
        if (resp && Array.isArray(resp.message)) {
          message = resp.message[0] || 'Validation failed';
        } else if (resp && typeof resp.message === 'string') {
          message = resp.message || 'Validation failed';
        } else {
          message = exception.message || 'Validation failed';
        }
        status = HttpStatus.UNPROCESSABLE_ENTITY;
      }

      // Handle generic bad request (non-validation) -> map to 400 errorCode
      if (status === HttpStatus.BAD_REQUEST) {
        errorCode = ErrorCode.VAL_400;
        if (typeof exceptionResponse === 'object') {
          const resp = exceptionResponse as any;
          message = resp.message || exception.message || message;
        } else {
          message = exception.message || message;
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
        status = HttpStatus.UNAUTHORIZED;
        message = 'Unauthorised';
      }

      this.logger.error(`Exception: ${msg}`);
    }

    const errorResponse: ErrorResponse = {
      errorCode: errorCode as any,
      message,
    };

    response.status(status).json(errorResponse);
  }
}
