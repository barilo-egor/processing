package net.rcetech.meta.orders;

public record OrderStatusChange(OrderStatus from, OrderStatus to, OrderStatusTransition transition) { }
