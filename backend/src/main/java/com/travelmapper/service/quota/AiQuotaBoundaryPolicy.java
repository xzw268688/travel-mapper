package com.travelmapper.service.quota;

import org.springframework.stereotype.Component;

/**
 * Centralizes the BR-002 refund boundary. Change-04 translates AI outcomes into
 * these reasons; the quota service only performs the idempotent refund.
 */
@Component
public class AiQuotaBoundaryPolicy {

    public boolean shouldRefund(AiFailureReason reason) {
        return switch (reason) {
            case TIMEOUT, MODEL_ERROR, JSON_FAILURE, NO_OUTPUT_DISCONNECT -> true;
            case USER_CANCELLED, PARTIAL_OUTPUT -> false;
        };
    }
}
