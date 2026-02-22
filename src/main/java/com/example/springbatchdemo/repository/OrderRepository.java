package com.example.springbatchdemo.repository;

import com.example.springbatchdemo.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByExternalOrderId(String externalOrderId);

    List<Order> findByExternalOrderIdIn(Collection<String> externalOrderIds);
}
