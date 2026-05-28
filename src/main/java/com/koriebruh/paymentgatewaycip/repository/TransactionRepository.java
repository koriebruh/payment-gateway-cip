package com.koriebruh.paymentgatewaycip.repository;

import com.koriebruh.paymentgatewaycip.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    boolean existsByOrderId(String orderId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByOrderId(String orderId);
}
