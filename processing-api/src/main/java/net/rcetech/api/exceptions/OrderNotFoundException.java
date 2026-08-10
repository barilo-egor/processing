package net.rcetech.api.exceptions;

import lombok.Getter;

@Getter
public class OrderNotFoundException extends RuntimeException {

    private final String id;

    public OrderNotFoundException(String id) {
        super();
        this.id = id;
    }

}
