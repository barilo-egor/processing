package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class ClientAlreadyExistsException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public ClientAlreadyExistsException() {
        super("Bad request.");
        this.field = "username";
        this.description = "Username is already taken.";
    }

}
