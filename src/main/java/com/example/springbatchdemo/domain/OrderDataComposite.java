package com.example.springbatchdemo.domain;

/**
 * Aggregate that groups the three normalised entities extracted from a single
 * denormalized CSV row. Carried through the chunk pipeline and unpacked by
 * the composite writer.
 */
public class OrderDataComposite {

    private final Customer customer;
    private final Order order;
    private final OrderTransaction transaction;

    public OrderDataComposite(Customer customer, Order order, OrderTransaction transaction) {
        this.customer = customer;
        this.order = order;
        this.transaction = transaction;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Order getOrder() {
        return order;
    }

    public OrderTransaction getTransaction() {
        return transaction;
    }
}
