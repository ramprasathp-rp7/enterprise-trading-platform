import { describe, it, expect } from 'vitest';
import {
  ErrorCode,
  ERROR_CODE_MESSAGES,
  ErrorCodeCatalogue,
  errorCodeCatalogue,
} from './error-codes';
import { ErrorResponseHandler, errorResponseHandler, ProcessedError } from './error-response-handler';

describe('ErrorCodeCatalogue', () => {
  let catalogue: ErrorCodeCatalogue;

  beforeEach(() => {
    catalogue = new ErrorCodeCatalogue();
  });

  describe('getMessage', () => {
    it('should return message for valid error code ACC-404', () => {
      const message = catalogue.getMessage(ErrorCode.ACC_404);
      expect(message).toContain('account could not be found');
    });

    it('should return message for valid error code ACC-403', () => {
      const message = catalogue.getMessage(ErrorCode.ACC_403);
      expect(message).toContain('account is not active');
    });

    it('should return message for valid error code INS-404', () => {
      const message = catalogue.getMessage(ErrorCode.INS_404);
      expect(message).toContain('instrument is not one that can be traded');
    });

    it('should return message for valid error code ORD-400', () => {
      const message = catalogue.getMessage(ErrorCode.ORD_400);
      expect(message).toContain('not enough cash');
    });

    it('should return message for valid error code ORD-409', () => {
      const message = catalogue.getMessage(ErrorCode.ORD_409);
      expect(message).toContain('Not enough of the holding to sell');
    });

    it('should return message for valid error code TRANS-404', () => {
      const message = catalogue.getMessage(ErrorCode.TRANS_404);
      expect(message).toContain('transaction could not be found');
    });

    it('should return message for valid error code TRANS-400', () => {
      const message = catalogue.getMessage(ErrorCode.TRANS_400);
      expect(message).toContain('Insufficient funds');
    });

    it('should return message for valid error code VAL-422', () => {
      const message = catalogue.getMessage(ErrorCode.VAL_422);
      expect(message).toContain('field is not acceptable');
    });

    it('should return message for valid error code AUTH-401', () => {
      const message = catalogue.getMessage(ErrorCode.AUTH_401);
      expect(message).toContain('session has expired');
    });

    it('should return message for valid error code AUTH-409', () => {
      const message = catalogue.getMessage(ErrorCode.AUTH_409);
      expect(message).toContain('username is already taken');
    });

    it('should return fallback message for unknown error code', () => {
      const message = catalogue.getMessage('UNKNOWN-999');
      expect(message).toContain('unexpected error');
      expect(message).toContain('UNKNOWN-999');
    });

    it('should return fallback message for null error code', () => {
      const message = catalogue.getMessage(null);
      expect(message).toContain('unexpected error');
    });

    it('should return fallback message for undefined error code', () => {
      const message = catalogue.getMessage(undefined);
      expect(message).toContain('unexpected error');
    });

    it('should return fallback message for empty string error code', () => {
      const message = catalogue.getMessage('');
      expect(message).toContain('unexpected error');
    });
  });

  describe('isKnownCode', () => {
    it('should return true for known code ACC-404', () => {
      expect(catalogue.isKnownCode(ErrorCode.ACC_404)).toBe(true);
    });

    it('should return true for known code AUTH-409', () => {
      expect(catalogue.isKnownCode(ErrorCode.AUTH_409)).toBe(true);
    });

    it('should return false for unknown code', () => {
      expect(catalogue.isKnownCode('UNKNOWN-999')).toBe(false);
    });
  });

  describe('validateCompleteness', () => {
    it('should report all codes are mapped', () => {
      const result = catalogue.validateCompleteness();
      expect(result.isComplete).toBe(true);
      expect(result.unmappedCodes.length).toBe(0);
    });

    it('should have all error codes mapped', () => {
      const result = catalogue.validateCompleteness();
      expect(result.totalCodes).toBeGreaterThan(0);
      expect(result.mappedCount).toBe(result.totalCodes);
    });

    it('should include all required codes', () => {
      const result = catalogue.validateCompleteness();
      const mapping = catalogue.getFullMapping();

      // Verify all trade API codes
      expect(mapping[ErrorCode.ACC_404]).toBeDefined();
      expect(mapping[ErrorCode.ACC_403]).toBeDefined();
      expect(mapping[ErrorCode.INS_404]).toBeDefined();
      expect(mapping[ErrorCode.ORD_400]).toBeDefined();
      expect(mapping[ErrorCode.ORD_409]).toBeDefined();
      expect(mapping[ErrorCode.VAL_422]).toBeDefined();
      expect(mapping[ErrorCode.AUTH_401]).toBeDefined();

      // Verify all auth API codes
      expect(mapping[ErrorCode.AUTH_409]).toBeDefined();
      expect(mapping[ErrorCode.TRANS_404]).toBeDefined();
      expect(mapping[ErrorCode.TRANS_400]).toBeDefined();
    });
  });

  describe('getFullMapping', () => {
    it('should return all mappings', () => {
      const mapping = catalogue.getFullMapping();
      expect(Object.keys(mapping).length).toBeGreaterThan(0);
    });

    it('should contain all error codes with non-empty messages', () => {
      const mapping = catalogue.getFullMapping();
      Object.entries(mapping).forEach(([code, message]) => {
        expect(message).toBeTruthy();
        expect(message.length).toBeGreaterThan(0);
      });
    });
  });
});

describe('ErrorResponseHandler', () => {
  let handler: ErrorResponseHandler;

  beforeEach(() => {
    handler = new ErrorResponseHandler();
  });

  describe('processError - normal API errors', () => {
    it('should process error with ACC-404 code', () => {
      const result = handler.processError(404, {
        errorCode: 'ACC-404',
        message: 'Account not found',
      });

      expect(result.code).toBe('ACC-404');
      expect(result.message).toContain('account could not be found');
      expect(result.isNetworkError).toBe(false);
      expect(result.isUnknownCode).toBe(false);
    });

    it('should process error with ORD-400 code', () => {
      const result = handler.processError(400, {
        errorCode: 'ORD-400',
        message: 'Insufficient funds',
      });

      expect(result.code).toBe('ORD-400');
      expect(result.message).toContain('not enough cash');
      expect(result.isNetworkError).toBe(false);
    });

    it('should process error with VAL-422 code', () => {
      const result = handler.processError(422, {
        errorCode: 'VAL-422',
        message: 'Invalid input',
      });

      expect(result.code).toBe('VAL-422');
      expect(result.message).toContain('field is not acceptable');
    });

    it('should process error with AUTH-409 code', () => {
      const result = handler.processError(409, {
        errorCode: 'AUTH-409',
        message: 'Username already taken',
      });

      expect(result.code).toBe('AUTH-409');
      expect(result.message).toContain('username is already taken');
    });
  });

  describe('processError - network errors', () => {
    it('should handle status 0 as network error', () => {
      const result = handler.processError(0);

      expect(result.code).toBe('NETWORK_ERROR');
      expect(result.message).toContain('Service is unavailable');
      expect(result.isNetworkError).toBe(true);
      expect(result.isUnknownCode).toBe(false);
    });

    it('should handle status 0 even with error body', () => {
      const result = handler.processError(0, { errorCode: 'ACC-404' });

      expect(result.code).toBe('NETWORK_ERROR');
      expect(result.message).toContain('Service is unavailable');
      expect(result.isNetworkError).toBe(true);
    });

    it('should handle null error body as network error', () => {
      const result = handler.processError(500, null);

      expect(result.code).toBe('NETWORK_ERROR');
      expect(result.isNetworkError).toBe(true);
    });

    it('should handle undefined error body as network error', () => {
      const result = handler.processError(500);

      expect(result.code).toBe('NETWORK_ERROR');
      expect(result.isNetworkError).toBe(true);
    });
  });

  describe('processError - unknown error codes', () => {
    it('should handle unknown error code', () => {
      const result = handler.processError(400, {
        errorCode: 'UNKNOWN-999',
        message: 'Unknown error',
      });

      expect(result.code).toBe('UNKNOWN-999');
      expect(result.message).toContain('unexpected error');
      expect(result.message).toContain('UNKNOWN-999');
      expect(result.isUnknownCode).toBe(true);
      expect(result.isNetworkError).toBe(false);
    });

    it('should handle missing errorCode field', () => {
      const result = handler.processError(400, {
        message: 'Some error message',
      });

      expect(result.code).toBe('UNKNOWN_ERROR');
      expect(result.message).toContain('unexpected error');
      expect(result.isUnknownCode).toBe(true);
    });
  });

  describe('processError - various response formats', () => {
    it('should parse JSON string error response', () => {
      const result = handler.processError(404, '{"errorCode":"ACC-404","message":"Not found"}');

      expect(result.code).toBe('ACC-404');
      expect(result.isUnknownCode).toBe(false);
    });

    it('should handle malformed JSON gracefully', () => {
      const result = handler.processError(500, 'This is not JSON');

      expect(result.code).toBe('UNKNOWN_ERROR');
      expect(result.message).toContain('unexpected error');
    });

    it('should handle plain object without errorCode', () => {
      const result = handler.processError(500, { someField: 'value' });

      expect(result.code).toBe('UNKNOWN_ERROR');
    });

    it('should handle empty object', () => {
      const result = handler.processError(400, {});

      expect(result.code).toBe('UNKNOWN_ERROR');
    });
  });

  describe('Helper methods', () => {
    it('isErrorCode should correctly identify matching error code', () => {
      const error: ProcessedError = {
        code: 'ACC-404',
        message: 'Account not found',
        isNetworkError: false,
        isUnknownCode: false,
      };

      expect(handler.isErrorCode(error, 'ACC-404')).toBe(true);
      expect(handler.isErrorCode(error, 'ORD-400')).toBe(false);
    });

    it('isNetworkError should identify network errors', () => {
      const networkError: ProcessedError = {
        code: 'NETWORK_ERROR',
        message: 'Service unavailable',
        isNetworkError: true,
        isUnknownCode: false,
      };

      const apiError: ProcessedError = {
        code: 'ACC-404',
        message: 'Account not found',
        isNetworkError: false,
        isUnknownCode: false,
      };

      expect(handler.isNetworkError(networkError)).toBe(true);
      expect(handler.isNetworkError(apiError)).toBe(false);
    });

    it('isUnknownCode should identify unknown codes', () => {
      const unknownError: ProcessedError = {
        code: 'UNKNOWN-999',
        message: 'Unknown error',
        isNetworkError: false,
        isUnknownCode: true,
      };

      const knownError: ProcessedError = {
        code: 'ACC-404',
        message: 'Account not found',
        isNetworkError: false,
        isUnknownCode: false,
      };

      expect(handler.isUnknownCode(unknownError)).toBe(true);
      expect(handler.isUnknownCode(knownError)).toBe(false);
    });
  });
});

describe('Singleton instances', () => {
  it('should provide singleton errorCodeCatalogue', () => {
    const result = errorCodeCatalogue.getMessage(ErrorCode.ACC_404);
    expect(result).toContain('account could not be found');
  });

  it('should provide singleton errorResponseHandler', () => {
    const result = errorResponseHandler.processError(404, {
      errorCode: 'ACC-404',
    });
    expect(result.code).toBe('ACC-404');
  });
});

describe('Error rendering completeness', () => {
  it('every code in Trade API catalogue maps to a message', () => {
    const tradeCodes = [
      ErrorCode.ACC_404,
      ErrorCode.ACC_403,
      ErrorCode.INS_404,
      ErrorCode.ORD_400,
      ErrorCode.ORD_409,
      ErrorCode.VAL_422,
      ErrorCode.AUTH_401,
    ];

    tradeCodes.forEach((code) => {
      const message = errorCodeCatalogue.getMessage(code);
      expect(message).toBeTruthy();
      expect(message).not.toContain('null');
      expect(message).not.toContain('undefined');
    });
  });

  it('every code in Auth API catalogue maps to a message', () => {
    const authCodes = [ErrorCode.AUTH_401, ErrorCode.AUTH_409, ErrorCode.VAL_422];

    authCodes.forEach((code) => {
      const message = errorCodeCatalogue.getMessage(code);
      expect(message).toBeTruthy();
      expect(message).not.toContain('null');
      expect(message).not.toContain('undefined');
    });
  });

  it('an unrecognised code falls back to a readable sentence', () => {
    const message = errorCodeCatalogue.getMessage('FUTURE-CODE-123');
    expect(message).toContain('unexpected error');
    expect(message).not.toContain('null');
    expect(message).not.toContain('undefined');
    expect(message.length).toBeGreaterThan(20); // Meaningful sentence
  });

  it('a request that never reached a service renders a readable sentence', () => {
    const handler = new ErrorResponseHandler();
    const result = handler.processError(0); // Status 0 = network error

    expect(result.isNetworkError).toBe(true);
    expect(result.message).toContain('Service is unavailable');
    expect(result.message).not.toContain('null');
    expect(result.message).not.toContain('undefined');
    expect(result.message.length).toBeGreaterThan(20); // Meaningful sentence
  });

  it('catalogue validation confirms all codes are mapped', () => {
    const validation = errorCodeCatalogue.validateCompleteness();
    expect(validation.isComplete).toBe(true);
    expect(validation.unmappedCodes).toHaveLength(0);

    // Spot check that we have at least the minimum required codes
    expect(validation.totalCodes).toBeGreaterThanOrEqual(10);
  });
});
