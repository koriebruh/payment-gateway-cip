package com.koriebruh.paymentgatewaycip.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void insufficientBalance_ShouldReturn422() {
        BusinessException ex = BusinessException.insufficientBalance("123");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void billerFailure_ShouldReturn502() {
        BusinessException ex = BusinessException.billerFailure("Fail");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getErrorCode()).isEqualTo("BILLER_FAILURE");
    }

    @Test
    void duplicateOrder_ShouldReturn409() {
        BusinessException ex = BusinessException.duplicateOrder("ORD");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_ORDER");
    }

    @Test
    void duplicateIdempotencyKey_ShouldReturn409() {
        BusinessException ex = BusinessException.duplicateIdempotencyKey("IDEMP");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_IDEMPOTENCY_KEY");
    }

    @Test
    void invalidChannel_ShouldReturn400() {
        BusinessException ex = BusinessException.invalidChannel("CH");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CHANNEL");
    }

    @Test
    void notFound_ShouldReturn404() {
        BusinessException ex = BusinessException.notFound("Msg");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("NOT_FOUND");
    }
}
