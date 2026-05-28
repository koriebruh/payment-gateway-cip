package com.koriebruh.paymentgatewaycip.mock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMockTest {

    @Test
    void coreBankingClientMock_ShouldReturnSuccess_WhenAccountIsValid() {
        CoreBankingClientMock mock = new CoreBankingClientMock();
        CoreBankingClient.CoreBankingResponse response = mock.debit("12345", BigDecimal.TEN, "ORD-1", "trace-1", "jwt");
        
        assertThat(response.success()).isTrue();
        assertThat(response.corebankReference()).isNotNull().startsWith("CB-");
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void coreBankingClientMock_ShouldReturnFailure_WhenAccountStartsWith99() {
        CoreBankingClientMock mock = new CoreBankingClientMock();
        CoreBankingClient.CoreBankingResponse response = mock.debit("9945", BigDecimal.TEN, "ORD-1", "trace-1", "jwt");
        
        assertThat(response.success()).isFalse();
        assertThat(response.corebankReference()).isNull();
        assertThat(response.failureReason()).isEqualTo("Insufficient balance");
    }

    @Test
    void billerClientMock_ShouldReturnSuccess_WhenMethodIsValid() {
        BillerClientMock mock = new BillerClientMock();
        BillerClient.BillerResponse response = mock.pay("ORD-1", BigDecimal.TEN, "VIRTUAL_ACCOUNT", "trace-1", "jwt");
        
        assertThat(response.success()).isTrue();
        assertThat(response.billerReference()).isNotNull().startsWith("BL-");
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void billerClientMock_ShouldReturnFailure_WhenMethodIsFail() {
        BillerClientMock mock = new BillerClientMock();
        BillerClient.BillerResponse response = mock.pay("ORD-1", BigDecimal.TEN, "FAIL", "trace-1", null);
        
        assertThat(response.success()).isFalse();
        assertThat(response.billerReference()).isNull();
        assertThat(response.failureReason()).isEqualTo("Biller service unavailable");
    }
}
