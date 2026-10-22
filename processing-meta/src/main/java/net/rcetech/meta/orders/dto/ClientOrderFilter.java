package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;

public record ClientOrderFilter(OrderStatus status, RequestMethod method) {}
