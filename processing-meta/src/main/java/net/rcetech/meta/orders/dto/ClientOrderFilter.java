package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;

import java.util.Set;

public record ClientOrderFilter(Set<OrderStatus> status, Set<RequestMethod> method) {}
