package com.example.springbatchdemo.job;

import com.example.springbatchdemo.domain.Customer;
import com.example.springbatchdemo.domain.Order;
import com.example.springbatchdemo.domain.OrderDataComposite;
import com.example.springbatchdemo.domain.OrderTransaction;
import com.example.springbatchdemo.repository.CustomerRepository;
import com.example.springbatchdemo.repository.OrderRepository;
import com.example.springbatchdemo.repository.OrderTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA-integrated chunk writer. Upserts customers, orders, and transactions
 * via Spring Data JPA repositories within the batch step transaction.
 * Uses bulk find-by-external-id and saveAll to avoid N+1.
 */
@Component
public class OrderDataCompositeJpaItemWriter implements ItemWriter<OrderDataComposite> {

    private static final Logger log = LoggerFactory.getLogger(OrderDataCompositeJpaItemWriter.class);

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderTransactionRepository orderTransactionRepository;

    public OrderDataCompositeJpaItemWriter(CustomerRepository customerRepository,
                                           OrderRepository orderRepository,
                                           OrderTransactionRepository orderTransactionRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.orderTransactionRepository = orderTransactionRepository;
    }

    @Override
    public void write(Chunk<? extends OrderDataComposite> chunk) {
        List<? extends OrderDataComposite> items = chunk.getItems();
        if (items.isEmpty()) {
            return;
        }

        log.debug("Writing chunk of {} composite items via JPA", items.size());

        Map<String, Long> customerIdMap = upsertCustomersAndResolveIds(items);
        Map<String, Long> orderIdMap = upsertOrdersAndResolveIds(items, customerIdMap);
        upsertTransactions(items, orderIdMap);
    }

    private Map<String, Long> upsertCustomersAndResolveIds(List<? extends OrderDataComposite> items) {
        Map<String, Customer> uniqueByExternalId = new LinkedHashMap<>();
        for (OrderDataComposite item : items) {
            Customer c = item.getCustomer();
            uniqueByExternalId.putIfAbsent(c.getExternalId(), c);
        }

        Set<String> externalIds = uniqueByExternalId.keySet();
        List<Customer> existing = customerRepository.findByExternalIdIn(externalIds);
        Map<String, Customer> existingByExternalId = existing.stream()
                .collect(Collectors.toMap(Customer::getExternalId, c -> c));

        LocalDateTime now = LocalDateTime.now();
        List<Customer> toSave = new ArrayList<>();
        for (Map.Entry<String, Customer> e : uniqueByExternalId.entrySet()) {
            String externalId = e.getKey();
            Customer fromChunk = e.getValue();
            Customer entity = existingByExternalId.get(externalId);
            if (entity != null) {
                entity.setName(fromChunk.getName());
                entity.setEmail(fromChunk.getEmail());
                entity.setUpdatedAt(now);
                toSave.add(entity);
            } else {
                if (fromChunk.getCreatedAt() == null) {
                    fromChunk.setCreatedAt(now);
                }
                fromChunk.setUpdatedAt(now);
                toSave.add(fromChunk);
            }
        }

        List<Customer> saved = customerRepository.saveAll(toSave);
        log.debug("Upserted {} unique customers", saved.size());

        Map<String, Long> idMap = new LinkedHashMap<>();
        for (Customer c : saved) {
            idMap.put(c.getExternalId(), c.getId());
        }
        return idMap;
    }

    private Map<String, Long> upsertOrdersAndResolveIds(List<? extends OrderDataComposite> items,
                                                        Map<String, Long> customerIdMap) {
        Map<String, Order> uniqueByExternalOrderId = new LinkedHashMap<>();
        for (OrderDataComposite item : items) {
            Order o = item.getOrder();
            Long customerId = customerIdMap.get(o.getCustomerExternalId());
            if (customerId == null) {
                log.warn("Customer ID not found for external_id={}, skipping order {}",
                        o.getCustomerExternalId(), o.getExternalOrderId());
                continue;
            }
            o.setCustomerId(customerId);
            uniqueByExternalOrderId.putIfAbsent(o.getExternalOrderId(), o);
        }

        if (uniqueByExternalOrderId.isEmpty()) {
            return Map.of();
        }

        Set<String> externalOrderIds = uniqueByExternalOrderId.keySet();
        List<Order> existing = orderRepository.findByExternalOrderIdIn(externalOrderIds);
        Map<String, Order> existingByExternalOrderId = existing.stream()
                .collect(Collectors.toMap(Order::getExternalOrderId, o -> o));

        LocalDateTime now = LocalDateTime.now();
        List<Order> toSave = new ArrayList<>();
        for (Map.Entry<String, Order> e : uniqueByExternalOrderId.entrySet()) {
            String externalOrderId = e.getKey();
            Order fromChunk = e.getValue();
            Order entity = existingByExternalOrderId.get(externalOrderId);
            if (entity != null) {
                entity.setTotalAmount(fromChunk.getTotalAmount());
                entity.setStatus(fromChunk.getStatus());
                entity.setUpdatedAt(now);
                entity.setCustomerId(fromChunk.getCustomerId());
                toSave.add(entity);
            } else {
                if (fromChunk.getCreatedAt() == null) {
                    fromChunk.setCreatedAt(now);
                }
                fromChunk.setUpdatedAt(now);
                toSave.add(fromChunk);
            }
        }

        List<Order> saved = orderRepository.saveAll(toSave);
        log.debug("Upserted {} unique orders", saved.size());

        Map<String, Long> idMap = new LinkedHashMap<>();
        for (Order o : saved) {
            idMap.put(o.getExternalOrderId(), o.getId());
        }
        return idMap;
    }

    private void upsertTransactions(List<? extends OrderDataComposite> items, Map<String, Long> orderIdMap) {
        List<OrderTransaction> toSave = new ArrayList<>();
        for (OrderDataComposite item : items) {
            OrderTransaction txn = item.getTransaction();
            Long orderId = orderIdMap.get(txn.getOrderExternalId());
            if (orderId == null) {
                log.warn("Order ID not found for external_order_id={}, skipping transaction {}",
                        txn.getOrderExternalId(), txn.getExternalTransactionId());
                continue;
            }
            txn.setOrderId(orderId);
            toSave.add(txn);
        }

        if (toSave.isEmpty()) {
            return;
        }

        Set<String> externalTxnIds = toSave.stream()
                .map(OrderTransaction::getExternalTransactionId)
                .collect(Collectors.toSet());
        List<OrderTransaction> existing = orderTransactionRepository.findByExternalTransactionIdIn(externalTxnIds);
        Map<String, OrderTransaction> existingByExternalId = existing.stream()
                .collect(Collectors.toMap(OrderTransaction::getExternalTransactionId, t -> t));

        LocalDateTime now = LocalDateTime.now();
        List<OrderTransaction> merged = new ArrayList<>();
        for (OrderTransaction fromChunk : toSave) {
            OrderTransaction entity = existingByExternalId.get(fromChunk.getExternalTransactionId());
            if (entity != null) {
                entity.setAmount(fromChunk.getAmount());
                entity.setStatus(fromChunk.getStatus());
                entity.setUpdatedAt(now);
                entity.setOrderId(fromChunk.getOrderId());
                merged.add(entity);
            } else {
                if (fromChunk.getCreatedAt() == null) {
                    fromChunk.setCreatedAt(now);
                }
                fromChunk.setUpdatedAt(now);
                merged.add(fromChunk);
            }
        }

        orderTransactionRepository.saveAll(merged);
        log.debug("Upserted {} transactions", merged.size());
    }
}
