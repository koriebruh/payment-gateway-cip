package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.exceptions.BusinessException;
import com.koriebruh.paymentgatewaycip.mock.BillerClient;
import com.koriebruh.paymentgatewaycip.mock.CoreBankingClient;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Observed(name = "payment.service")
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository transactionRepository;

    private final CoreBankingClient coreBankingClient;

    private final BillerClient billerClient;

    private final PaymentTransactionHelper transactionHelper;

    private final Executor taskExecutor;

    public PaymentService(TransactionRepository transactionRepository,
                          CoreBankingClient coreBankingClient,
                          BillerClient billerClient,
                          PaymentTransactionHelper transactionHelper,
                          @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.transactionRepository = transactionRepository;
        this.coreBankingClient = coreBankingClient;
        this.billerClient = billerClient;
        this.transactionHelper = transactionHelper;
        this.taskExecutor = taskExecutor;
    }

    @Observed(name = "payment.process", contextualName = "processPayment")
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        var traceId = MDC.get("traceId");
        var jwtToken = extractJwtToken();

        var existingTxOpt = transactionHelper.getExistingIdempotentResponse(idempotencyKey, request, traceId);
        if (existingTxOpt.isPresent()) {
            return existingTxOpt.get();
        }

        var tx = transactionHelper.savePending(request, parseChannel(request.channel()), idempotencyKey);
        log.info("Transaction persisted id={} status=PENDING traceId={}", tx.getId(), traceId);

        try {
            // Sequential execution to prevent Biller payment if CoreBank debit fails
            var bankResp = callCoreBank(tx.getAccount(), tx.getAmount(), request.orderId(), traceId, jwtToken).join();
            if (!bankResp.success()) {
                var reason = "CoreBank: %s".formatted(bankResp.failureReason());
                log.warn("Payment rejected — reason={} traceId={}", reason, traceId);
                return transactionHelper.failTransaction(tx, reason, traceId);
            }

            var billerResp = callBiller(request.orderId(), tx.getAmount(), request.paymentMethod(), traceId, jwtToken).join();
            if (!billerResp.success()) {
                var reason = "Biller: %s".formatted(billerResp.failureReason());
                log.warn("Payment rejected — reason={} traceId={}", reason, traceId);
                // Note: Real system would require a compensating transaction to reverse CoreBank debit here (Saga)
                return transactionHelper.failTransaction(tx, reason, traceId);
            }

            log.info("Payment approved — corebankRef={} billerRef={} traceId={}",
                    bankResp.corebankReference(), billerResp.billerReference(), traceId);
            return transactionHelper.succeedTransaction(tx, bankResp.corebankReference(), billerResp.billerReference(), traceId);

        } catch (Exception ex) {
            log.error("Unexpected error during processPayment traceId={} error={}", traceId, ex.getMessage(), ex);
            return transactionHelper.failTransaction(tx, "Internal Processing Error", traceId);
        }
    }


    @Observed(name = "payment.status", contextualName = "getPaymentStatus")
    @Transactional(readOnly = true)
    public PaymentResponse getStatus(String orderId) {
        var tx = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Transaction not found: %s".formatted(orderId)));

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                tx.getStatus().name(),
                tx.getCorebankReference(),
                tx.getBillerReference(),
                "Transaction status retrieved"
        );
    }

    @CircuitBreaker(name = "corebank", fallbackMethod = "coreBankFallback")
    @TimeLimiter(name = "corebank", fallbackMethod = "coreBankFallback")
    @Observed(name = "corebank.debit", contextualName = "coreBankDebit")
    protected CompletableFuture<CoreBankingClient.CoreBankingResponse> callCoreBank(
            String account, BigDecimal amount, String orderId, String traceId, String jwtToken) {
        return CompletableFuture.supplyAsync(() -> coreBankingClient.debit(account, amount, orderId, traceId, jwtToken), taskExecutor);
    }

    protected CompletableFuture<CoreBankingClient.CoreBankingResponse> coreBankFallback(
            String account, BigDecimal amount, String orderId, String traceId, String jwtToken, Throwable ex) {
        log.warn("event=corebank_fallback orderId={} reason={}", orderId, ex.getMessage());
        return CompletableFuture.completedFuture(new CoreBankingClient.CoreBankingResponse(false, null, "Service Unavailable: %s".formatted(ex.getMessage())));
    }

    @CircuitBreaker(name = "biller", fallbackMethod = "billerFallback")
    @TimeLimiter(name = "biller", fallbackMethod = "billerFallback")
    @Observed(name = "biller.pay", contextualName = "billerPay")
    protected CompletableFuture<BillerClient.BillerResponse> callBiller(
            String orderId, BigDecimal amount, String paymentMethod, String traceId, String jwtToken) {
        return CompletableFuture.supplyAsync(() -> billerClient.pay(orderId, amount, paymentMethod, traceId, jwtToken), taskExecutor);
    }

    protected CompletableFuture<BillerClient.BillerResponse> billerFallback(
            String orderId, BigDecimal amount, String paymentMethod, String traceId, String jwtToken, Throwable ex) {
        log.warn("event=biller_fallback orderId={} reason={}", orderId, ex.getMessage());
        return CompletableFuture.completedFuture(new BillerClient.BillerResponse(false, null, "Service Unavailable: %s".formatted(ex.getMessage())));
    }

    private String extractJwtToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return switch (auth) {
            case JwtAuthenticationToken jwt -> jwt.getToken().getTokenValue();
            case null, default -> throw BusinessException.unauthorized();
        };
    }

    private Transaction.Channel parseChannel(String raw) {
        try {
            return Transaction.Channel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidChannel(raw);
        }
    }
}
