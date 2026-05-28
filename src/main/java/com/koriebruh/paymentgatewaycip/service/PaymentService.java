package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.exceptions.BusinessException;
import com.koriebruh.paymentgatewaycip.mock.BillerClient;
import com.koriebruh.paymentgatewaycip.mock.CoreBankingClient;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

        var existingTxOpt = transactionHelper.getExistingIdempotentResponse(idempotencyKey, traceId);
        if (existingTxOpt.isPresent()) {
            return existingTxOpt.get();
        }

        var tx = transactionHelper.savePending(request, parseChannel(request.channel()), idempotencyKey);
        log.info("Transaction persisted id={} status=PENDING traceId={}", tx.getId(), traceId);

        var bankFuture = CompletableFuture.supplyAsync(
                () -> callCoreBank(tx.getAccount(), tx.getAmount(), request.orderId(), traceId, jwtToken), taskExecutor);
        var billerFuture = CompletableFuture.supplyAsync(
                () -> callBiller(request.orderId(), tx.getAmount(), request.paymentMethod(), traceId, jwtToken), taskExecutor);

        CompletableFuture.allOf(bankFuture, billerFuture).join();

        var bankResp = bankFuture.join();
        var billerResp = billerFuture.join();

        if (bankResp.success() && billerResp.success()) {
            log.info("Payment approved — corebankRef={} billerRef={} traceId={}",
                    bankResp.corebankReference(), billerResp.billerReference(), traceId);
            return transactionHelper.succeedTransaction(tx, bankResp.corebankReference(), billerResp.billerReference(), traceId);
        }

        var reason = buildFailureReason(bankResp, billerResp);
        log.warn("Payment rejected — reason={} traceId={}", reason, traceId);
        return transactionHelper.failTransaction(tx, reason, traceId);
    }

    private String buildFailureReason(CoreBankingClient.CoreBankingResponse bankResp, BillerClient.BillerResponse billerResp) {
        if (!bankResp.success() && !billerResp.success()) {
            return "CoreBank: " + bankResp.failureReason() + " | Biller: " + billerResp.failureReason();
        }
        return !bankResp.success() ? "CoreBank: " + bankResp.failureReason() : "Biller: " + billerResp.failureReason();
    }

    @Observed(name = "payment.status", contextualName = "getPaymentStatus")
    @Transactional(readOnly = true)
    public PaymentResponse getStatus(String orderId) {
        var tx = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Transaction not found: " + orderId));

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                tx.getStatus().name(),
                tx.getCorebankReference(),
                tx.getBillerReference(),
                "Transaction status retrieved"
        );
    }

    @CircuitBreaker(name = "corebank")
    @Observed(name = "corebank.debit", contextualName = "coreBankDebit")
    protected CoreBankingClient.CoreBankingResponse callCoreBank(
            String account, BigDecimal amount, String orderId, String traceId, String jwtToken) {
        return coreBankingClient.debit(account, amount, orderId, traceId, jwtToken);
    }

    @CircuitBreaker(name = "biller")
    @Observed(name = "biller.pay", contextualName = "billerPay")
    protected BillerClient.BillerResponse callBiller(
            String orderId, BigDecimal amount, String paymentMethod, String traceId, String jwtToken) {
        return billerClient.pay(orderId, amount, paymentMethod, traceId, jwtToken);
    }

    private String extractJwtToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    private Transaction.Channel parseChannel(String raw) {
        try {
            return Transaction.Channel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidChannel(raw);
        }
    }
}
