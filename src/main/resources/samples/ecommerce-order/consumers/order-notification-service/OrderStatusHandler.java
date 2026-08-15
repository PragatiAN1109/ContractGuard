package com.example.notifications;

import com.example.orders.OrderEvent;
import com.example.orders.OrderStatus;

/**
 * Generated against order-v1. CREATED means "a customer just placed an order", so it triggers
 * the new-order welcome email.
 */
public class OrderStatusHandler {

    private final NotificationSender sender;

    public OrderStatusHandler(NotificationSender sender) {
        this.sender = sender;
    }

    public void handle(OrderEvent order) {
        switch (order.getStatus()) {
            case CREATED -> sendNewOrderNotification(order);
            case PAID -> sender.send(order.getCustomerEmail(), "Payment received");
            case SHIPPED -> sender.send(order.getCustomerEmail(), "Your order is on its way");
            case DELIVERED -> sender.send(order.getCustomerEmail(), "Delivered");
            case CANCELLED -> sender.send(order.getCustomerEmail(), "Order cancelled");
        }
    }

    private void sendNewOrderNotification(OrderEvent order) {
        sender.send(order.getCustomerEmail(), "Thanks for your order!");
        sender.scheduleAbandonedCartFollowUp(order.getOrderId());
    }

    public boolean isNewOrder(OrderEvent order) {
        return order.getStatus() == OrderStatus.CREATED;
    }
}
