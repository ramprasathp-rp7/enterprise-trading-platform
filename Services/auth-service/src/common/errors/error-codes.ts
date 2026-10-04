export enum ErrorCode {
  AUTH_401 = 'AUTH-401',
  AUTH_409 = 'AUTH-409',
  VAL_422 = 'VAL-422',
  VAL_400 = 'VAL-400',
}

export interface ErrorResponse {
  errorCode: ErrorCode;
  message: string;
}
