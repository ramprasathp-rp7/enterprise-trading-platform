# Typed errors for the four failure modes the Sprint 4 README names

from __future__ import annotations

class FauxnanceError(Exception):
    # Base class for every error this package raises against the Fauxnance API
    '''
    QuotaExhaustedError    429, Retry-After present  -> stop the whole run
    SymbolRequestError     other 4xx (401/404/400)    -> fail this symbol, continue
    FauxnanceConnectionError  no response reached us   -> retry with backoff, then give up
    MalformedCandleError   200, but the row is bad     -> transform's call, not extract's
    '''
    
class QuotaExhaustedError(FauxnanceError):
    # HTTP 429. The daily quota is spent. Not recoverable within this run

    def __init__(self, retry_after_seconds: int | None):
        self.retry_after_seconds = retry_after_seconds
        message = "Fauxnance daily quota exhausted."
        if retry_after_seconds is not None:
            message += f" Resets in {retry_after_seconds}s (per Retry-After)."
        super().__init__(message)


class SymbolRequestError(FauxnanceError):
    # Another 4xx: bad/missing key (401), unknown symbol (404), bad range (400).

    def __init__(self, symbol: str, status_code: int, message: str):
        self.symbol = symbol
        self.status_code = status_code
        super().__init__(f"{symbol}: HTTP {status_code}: {message}")


class FauxnanceConnectionError(FauxnanceError):
    # Nothing reached the service: a connection error or a timeout.
    ''' FauxnanceConnectionError  no response reached us   -> retry with backoff, then give up
    '''
    
class MalformedCandleError(FauxnanceError):
    # A candle's data is structurally invalid in a way transform() decided

    def __init__(self, symbol: str, date: str, reason: str):
        self.symbol = symbol
        self.date = date
        self.reason = reason
        super().__init__(f"{symbol} {date}: {reason}")
