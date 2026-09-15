package com.tradingplatform.domain.exception;

/**
 * Thrown when an optimistic lock conflict is detected during an update.
 * This occurs when another transaction has modified the same row since it was last read,
 * typically detected by version column mismatch.
 * 
 * Used for concurrent order placement scenarios where multiple orders arrive at the same time
 * and attempt to debit the same account.
 */
public final class OptimisticLockException extends DomainException {

    private final String resourceType;
    private final Object resourceId;

    public OptimisticLockException(String resourceType, Object resourceId) {
        super(com.tradingplatform.domain.enums.ErrorCode.ORD_409, 
              "Optimistic lock conflict: " + resourceType + " " + resourceId + " was modified by another transaction");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
