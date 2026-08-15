package com.example.analytics;

import com.example.orders.OrderEvent;
import com.example.orders.OrderStatus;

/**
 * Only counts fulfilment outcomes. It never gives the enum default its own behaviour, so an
 * unknown symbol resolving to CREATED changes nothing here.
 */
public class FulfilmentMetricsCollector {

    private final MetricsClient metrics;

    public FulfilmentMetricsCollector(MetricsClient metrics) {
        this.metrics = metrics;
    }

    public void record(OrderEvent order) {
        switch (order.getStatus()) {
            case SHIPPED -> metrics.increment("orders.shipped");
            case DELIVERED -> metrics.increment("orders.delivered");
            case CANCELLED -> metrics.increment("orders.cancelled");
            default -> metrics.increment("orders.other");
        }
    }

    public boolean isTerminal(OrderEvent order) {
        return order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED;
    }
}
