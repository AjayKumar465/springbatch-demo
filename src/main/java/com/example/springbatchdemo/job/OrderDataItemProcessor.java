package com.example.springbatchdemo.job;

import com.example.springbatchdemo.domain.Customer;
import com.example.springbatchdemo.domain.Order;
import com.example.springbatchdemo.domain.OrderDataComposite;
import com.example.springbatchdemo.domain.OrderTransaction;
import com.example.springbatchdemo.domain.RawOrderRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Pure in-memory transformation: one denormalized CSV row becomes a
 * {@link OrderDataComposite} containing Customer, Order, and OrderTransaction.
 * Returns {@code null} to skip rows that fail validation.
 */
@Component
public class OrderDataItemProcessor implements ItemProcessor<RawOrderRecord, OrderDataComposite> {

    private static final Logger log = LoggerFactory.getLogger(OrderDataItemProcessor.class);

    @Override
    @Nullable
    public OrderDataComposite process(@NonNull RawOrderRecord raw) {
        if (!isValid(raw)) {
            return null;
        }

        Customer customer = new Customer(
                raw.getCustomerId().trim(),
                raw.getCustomerName().trim(),
                raw.getCustomerEmail().trim()
        );

        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(raw.getOrderDate().trim());
        } catch (DateTimeParseException e) {
            log.warn("Skipping row: invalid order date '{}' for order {}", raw.getOrderDate(), raw.getOrderId());
            return null;
        }

        Order order = new Order(
                raw.getCustomerId().trim(),
                raw.getOrderId().trim(),
                orderDate,
                raw.getOrderAmount(),
                raw.getOrderStatus().trim().toUpperCase()
        );

        OrderTransaction transaction = new OrderTransaction(
                raw.getOrderId().trim(),
                raw.getTransactionId().trim(),
                raw.getTransactionType().trim().toUpperCase(),
                raw.getTransactionAmount(),
                raw.getTransactionStatus().trim().toUpperCase()
        );

        return new OrderDataComposite(customer, order, transaction);
    }

    private boolean isValid(RawOrderRecord raw) {
        if (isBlank(raw.getCustomerId()) || isBlank(raw.getCustomerName()) || isBlank(raw.getCustomerEmail())) {
            log.warn("Skipping row: missing customer fields [id={}, name={}, email={}]",
                    raw.getCustomerId(), raw.getCustomerName(), raw.getCustomerEmail());
            return false;
        }
        if (isBlank(raw.getOrderId()) || isBlank(raw.getOrderDate()) || raw.getOrderAmount() == null) {
            log.warn("Skipping row: missing order fields [orderId={}, date={}, amount={}]",
                    raw.getOrderId(), raw.getOrderDate(), raw.getOrderAmount());
            return false;
        }
        if (isBlank(raw.getTransactionId()) || raw.getTransactionAmount() == null) {
            log.warn("Skipping row: missing transaction fields [txnId={}, amount={}]",
                    raw.getTransactionId(), raw.getTransactionAmount());
            return false;
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
