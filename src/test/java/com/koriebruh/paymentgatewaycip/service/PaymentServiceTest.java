package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.exceptions.BusinessException;
import com.koriebruh.paymentgatewaycip.mock.BillerClient;
import com.koriebruh.paymentgatewaycip.mock.CoreBankingClient;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CoreBankingClient coreBankingClient;

    @Mock
    private BillerClient billerClient;

    @Mock
    private PaymentTransactionHelper transactionHelper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Use a direct executor to run async tasks synchronously in the same thread for testing
        Executor directExecutor = Runnable::run;
        paymentService = new PaymentService(transactionRepository, coreBankingClient, billerClient, transactionHelper, directExecutor);
        
        // Mock Security Context (leniently)
        SecurityContext securityContext = mock(SecurityContext.class, withSettings().lenient());
        Jwt jwt = mock(Jwt.class, withSettings().lenient());
        when(jwt.getTokenValue()).thenReturn("mock-token");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processPayment_WhenIdempotencyKeyExists_ShouldReturnExistingResponse() {
        String idempotencyKey = "IDEMP-123";
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse existingResponse = new PaymentResponse("TX-1", "ORD-1", "SUCCESS", "CB-1", "BL-1", "Msg");
        
        when(transactionHelper.getExistingIdempotentResponse(eq(idempotencyKey), any())).thenReturn(Optional.of(existingResponse));

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo("ORD-1");
        assertThat(response.status()).isEqualTo("SUCCESS");
        
        verify(transactionHelper, never()).savePending(any(), any(), anyString());
    }

    @Test
    void processPayment_WhenBothClientsSucceed_ShouldReturnSuccess() {
        String idempotencyKey = "IDEMP-123";
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        Transaction pendingTx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .account("ORD-1")
                .amount(BigDecimal.TEN)
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        when(transactionHelper.getExistingIdempotentResponse(eq(idempotencyKey), any())).thenReturn(Optional.empty());
        when(transactionHelper.savePending(any(), eq(Transaction.Channel.MOBILE_BANKING), eq(idempotencyKey))).thenReturn(pendingTx);
        
        when(coreBankingClient.debit(eq("ORD-1"), eq(BigDecimal.TEN), eq("ORD-1"), any(), eq("mock-token")))
                .thenReturn(new CoreBankingClient.CoreBankingResponse(true, "CB-123", null));
        when(billerClient.pay(eq("ORD-1"), eq(BigDecimal.TEN), eq("VA"), any(), eq("mock-token")))
                .thenReturn(new BillerClient.BillerResponse(true, "BL-123", null));

        PaymentResponse successResponse = new PaymentResponse(pendingTx.getId().toString(), "ORD-1", "SUCCESS", "CB-123", "BL-123", "OK");
        when(transactionHelper.succeedTransaction(eq(pendingTx), eq("CB-123"), eq("BL-123"), any()))
                .thenReturn(successResponse);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(transactionHelper).succeedTransaction(pendingTx, "CB-123", "BL-123", null);
        verify(transactionHelper, never()).failTransaction(any(), anyString(), any());
    }

    @Test
    void processPayment_WhenCoreBankFails_ShouldReturnFailed() {
        String idempotencyKey = "IDEMP-123";
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        Transaction pendingTx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .account("ORD-1")
                .amount(BigDecimal.TEN)
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        when(transactionHelper.getExistingIdempotentResponse(anyString(), any())).thenReturn(Optional.empty());
        when(transactionHelper.savePending(any(), any(), anyString())).thenReturn(pendingTx);
        
        when(coreBankingClient.debit(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(new CoreBankingClient.CoreBankingResponse(false, null, "Insufficient funds"));
        when(billerClient.pay(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(new BillerClient.BillerResponse(true, "BL-123", null));

        PaymentResponse failResponse = new PaymentResponse(pendingTx.getId().toString(), "ORD-1", "FAILED", null, null, "Failed");
        when(transactionHelper.failTransaction(eq(pendingTx), anyString(), any())).thenReturn(failResponse);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(transactionHelper).failTransaction(eq(pendingTx), eq("CoreBank: Insufficient funds"), any());
    }
    
    @Test
    void processPayment_WhenBothFail_ShouldReturnFailed() {
        String idempotencyKey = "IDEMP-123";
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        Transaction pendingTx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .account("ORD-1")
                .amount(BigDecimal.TEN)
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        when(transactionHelper.getExistingIdempotentResponse(anyString(), any())).thenReturn(Optional.empty());
        when(transactionHelper.savePending(any(), any(), anyString())).thenReturn(pendingTx);
        
        when(coreBankingClient.debit(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(new CoreBankingClient.CoreBankingResponse(false, null, "Error CB"));
        when(billerClient.pay(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(new BillerClient.BillerResponse(false, null, "Error BL"));

        PaymentResponse failResponse = new PaymentResponse(pendingTx.getId().toString(), "ORD-1", "FAILED", null, null, "Failed");
        when(transactionHelper.failTransaction(eq(pendingTx), anyString(), any())).thenReturn(failResponse);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(transactionHelper).failTransaction(eq(pendingTx), eq("CoreBank: Error CB | Biller: Error BL"), any());
    }

    @Test
    void processPayment_WithInvalidChannel_ShouldThrowException() {
        PaymentRequest request = new PaymentRequest("ORD-1", "INVALID_CHANNEL", BigDecimal.TEN, "IDR", "VA");
        
        when(transactionHelper.getExistingIdempotentResponse(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(request, "IDEMP"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported channel");
    }

    @Test
    void getStatus_WhenFound_ShouldReturnStatus() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .status(Transaction.TransactionStatus.SUCCESS)
                .corebankReference("CB-1")
                .billerReference("BL-1")
                .build();
                
        when(transactionRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(tx));

        PaymentResponse response = paymentService.getStatus("ORD-1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.corebankReference()).isEqualTo("CB-1");
    }

    @Test
    void getStatus_WhenNotFound_ShouldThrowException() {
        when(transactionRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getStatus("ORD-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transaction not found");
    }
}
