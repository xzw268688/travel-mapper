package com.travelmapper.service.quota;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuotaBoundaryPolicyTest {

    private final AiQuotaBoundaryPolicy policy = new AiQuotaBoundaryPolicy();

    @Test
    void refundsOnlyWhenNoUsableOutputWasDelivered() {
        assertThat(policy.shouldRefund(AiFailureReason.TIMEOUT)).isTrue();
        assertThat(policy.shouldRefund(AiFailureReason.MODEL_ERROR)).isTrue();
        assertThat(policy.shouldRefund(AiFailureReason.JSON_FAILURE)).isTrue();
        assertThat(policy.shouldRefund(AiFailureReason.NO_OUTPUT_DISCONNECT)).isTrue();
        assertThat(policy.shouldRefund(AiFailureReason.USER_CANCELLED)).isFalse();
        assertThat(policy.shouldRefund(AiFailureReason.PARTIAL_OUTPUT)).isFalse();
    }
}
