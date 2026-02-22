package com.example.springbatchdemo;

import com.example.springbatchdemo.domain.OrderDataComposite;
import com.example.springbatchdemo.domain.RawOrderRecord;
import com.example.springbatchdemo.job.OrderDataItemProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDataItemProcessorTest {

    private OrderDataItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderDataItemProcessor();
    }

    @Test
    void shouldTransformValidRecord() {
        RawOrderRecord raw = buildValidRecord();

        OrderDataComposite result = processor.process(raw);

        assertThat(result).isNotNull();
        assertThat(result.getCustomer().getExternalId()).isEqualTo("CUST001");
        assertThat(result.getCustomer().getName()).isEqualTo("John Doe");
        assertThat(result.getCustomer().getEmail()).isEqualTo("john@example.com");
        assertThat(result.getOrder().getExternalOrderId()).isEqualTo("ORD001");
        assertThat(result.getOrder().getOrderDate().toString()).isEqualTo("2025-01-15");
        assertThat(result.getOrder().getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(result.getOrder().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTransaction().getExternalTransactionId()).isEqualTo("TXN001");
        assertThat(result.getTransaction().getTransactionType()).isEqualTo("PAYMENT");
    }

    @Test
    void shouldReturnNullForMissingCustomerId() {
        RawOrderRecord raw = buildValidRecord();
        raw.setCustomerId(null);

        assertThat(processor.process(raw)).isNull();
    }

    @Test
    void shouldReturnNullForMissingOrderId() {
        RawOrderRecord raw = buildValidRecord();
        raw.setOrderId("");

        assertThat(processor.process(raw)).isNull();
    }

    @Test
    void shouldReturnNullForInvalidDate() {
        RawOrderRecord raw = buildValidRecord();
        raw.setOrderDate("not-a-date");

        assertThat(processor.process(raw)).isNull();
    }

    @Test
    void shouldReturnNullForMissingTransactionAmount() {
        RawOrderRecord raw = buildValidRecord();
        raw.setTransactionAmount(null);

        assertThat(processor.process(raw)).isNull();
    }

    @Test
    void shouldTrimAndUppercaseStatusFields() {
        RawOrderRecord raw = buildValidRecord();
        raw.setOrderStatus("  completed  ");
        raw.setTransactionType("  payment  ");
        raw.setTransactionStatus("  success  ");

        OrderDataComposite result = processor.process(raw);

        assertThat(result).isNotNull();
        assertThat(result.getOrder().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTransaction().getTransactionType()).isEqualTo("PAYMENT");
        assertThat(result.getTransaction().getStatus()).isEqualTo("SUCCESS");
    }

    private RawOrderRecord buildValidRecord() {
        RawOrderRecord r = new RawOrderRecord();
        r.setCustomerId("CUST001");
        r.setCustomerName("John Doe");
        r.setCustomerEmail("john@example.com");
        r.setOrderId("ORD001");
        r.setOrderDate("2025-01-15");
        r.setOrderAmount(new BigDecimal("250.00"));
        r.setOrderStatus("COMPLETED");
        r.setTransactionId("TXN001");
        r.setTransactionType("PAYMENT");
        r.setTransactionAmount(new BigDecimal("250.00"));
        r.setTransactionStatus("SUCCESS");
        return r;
    }
}
