package com.example.returns;

import com.example.orders.OrderEvent;
import com.example.orders.OrderStatus;

/**
 * Already regenerated against order-v2: it names RETURNED, so nothing falls back to the default
 * for this consumer even though it also branches on CREATED.
 */
public class ReturnsProcessor {

    private final RefundGateway refunds;

    public ReturnsProcessor(RefundGateway refunds) {
        this.refunds = refunds;
    }

    public void process(OrderEvent order) {
        switch (order.getStatus()) {
            case RETURNED -> refunds.issueRefund(order.getOrderId(), order.getTotalCents());
            case CREATED -> refunds.openReturnWindow(order.getOrderId());
            case DELIVERED -> refunds.startReturnEligibility(order.getOrderId());
            default -> { }
        }
    }

    public boolean isReturned(OrderEvent order) {
        return order.getStatus() == OrderStatus.RETURNED;
    }
}
