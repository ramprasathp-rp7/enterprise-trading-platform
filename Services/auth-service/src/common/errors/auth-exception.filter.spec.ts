import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  HttpException,
  HttpStatus,
  NotFoundException,
  UnauthorizedException,
  UnprocessableEntityException,
} from '@nestjs/common';
import { AuthExceptionFilter } from './auth-exception.filter';
import { ErrorCode } from './error-codes';

describe('AuthExceptionFilter', () => {
  let filter: AuthExceptionFilter;
  let response: { status: jest.Mock; json: jest.Mock };
  let host: any;

  beforeEach(() => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    filter = new AuthExceptionFilter();
    response = { status: jest.fn(), json: jest.fn() };
    response.status.mockReturnValue(response);
    host = { switchToHttp: () => ({ getResponse: () => response }) };
  });

  afterEach(() => jest.restoreAllMocks());

  const sent = () => ({ status: response.status.mock.calls[0][0], body: response.json.mock.calls[0][0] });

  test('maps ValidationPipe array messages to VAL-422 with the first message', () => {
    filter.catch(new BadRequestException(['username too short', 'password too short']), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(body.errorCode).toBe(ErrorCode.VAL_422);
  });

  test('maps UnprocessableEntityException to VAL-422', () => {
    filter.catch(new UnprocessableEntityException('bad input'), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(body).toEqual({ errorCode: ErrorCode.VAL_422, message: 'bad input' });
  });

  test('maps a 400 whose body reports statusCode 422 to VAL-422', () => {
    filter.catch(new HttpException({ statusCode: 422, message: 'nested' }, HttpStatus.BAD_REQUEST), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(body.errorCode).toBe(ErrorCode.VAL_422);
  });

  test('maps a 422 with a non-object body using the exception message', () => {
    filter.catch(new HttpException('plain text', HttpStatus.UNPROCESSABLE_ENTITY), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(body).toEqual({ errorCode: ErrorCode.VAL_422, message: 'plain text' });
  });

  test('maps a non-validation bad request to VAL-400', () => {
    filter.catch(new BadRequestException('malformed json'), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body).toEqual({ errorCode: ErrorCode.VAL_400, message: 'malformed json' });
  });

  test('maps a bad request with a string body to VAL-400', () => {
    filter.catch(new HttpException('raw', HttpStatus.BAD_REQUEST), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body).toEqual({ errorCode: ErrorCode.VAL_400, message: 'raw' });
  });

  test('maps ConflictException to AUTH-409', () => {
    filter.catch(new ConflictException('Registration failed'), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.CONFLICT);
    expect(body).toEqual({ errorCode: ErrorCode.AUTH_409, message: 'Registration failed' });
  });

  test('maps UnauthorizedException to AUTH-401', () => {
    filter.catch(new UnauthorizedException('Unauthorised'), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNAUTHORIZED);
    expect(body).toEqual({ errorCode: ErrorCode.AUTH_401, message: 'Unauthorised' });
  });

  test('passes through other HTTP exceptions with their status', () => {
    filter.catch(new ForbiddenException('nope'), host);
    expect(sent().status).toBe(HttpStatus.FORBIDDEN);
    expect(sent().body.message).toBe('nope');
  });

  test('passes through HTTP exceptions with a string body', () => {
    filter.catch(new HttpException('gone', HttpStatus.GONE), host);
    expect(sent()).toEqual({ status: HttpStatus.GONE, body: { errorCode: 'INTERNAL_ERROR', message: 'gone' } });
  });

  test('keeps the status of NotFoundException', () => {
    filter.catch(new NotFoundException(), host);
    expect(sent().status).toBe(HttpStatus.NOT_FOUND);
  });

  test.each(['already taken', 'already exists'])('maps plain errors containing "%s" to AUTH-409', (fragment) => {
    filter.catch(new Error(`Username is ${fragment}`), host);
    expect(sent()).toEqual({
      status: HttpStatus.CONFLICT,
      body: { errorCode: ErrorCode.AUTH_409, message: 'Registration failed' },
    });
  });

  test.each(['User not found', 'Unknown user'])('maps plain error "%s" to AUTH-401', (msg) => {
    filter.catch(new Error(msg), host);
    expect(sent()).toEqual({
      status: HttpStatus.UNAUTHORIZED,
      body: { errorCode: ErrorCode.AUTH_401, message: 'Unauthorised' },
    });
  });

  test('maps any other plain error to 401 without leaking the message', () => {
    filter.catch(new Error('db connection refused'), host);
    const { status, body } = sent();
    expect(status).toBe(HttpStatus.UNAUTHORIZED);
    expect(body.message).toBe('Unauthorised');
    expect(console.error).toHaveBeenCalled();
  });

  test('maps non-Error values to a 500', () => {
    filter.catch('something odd', host);
    expect(sent()).toEqual({
      status: HttpStatus.INTERNAL_SERVER_ERROR,
      body: { errorCode: 'INTERNAL_ERROR', message: 'An unexpected error occurred' },
    });
  });
});
