package com.example.springbatchdemo.repository;

import com.example.springbatchdemo.domain.OrderTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderTransactionRepository extends JpaRepository<OrderTransaction, Long> {

    Optional<OrderTransaction> findByExternalTransactionId(String externalTransactionId);

    List<OrderTransaction> findByExternalTransactionIdIn(Collection<String> externalTransactionIds);
}
