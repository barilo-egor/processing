package net.rcetech.meta.support.dto;

import lombok.*;
import net.rcetech.meta.support.UserRole;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private Long id;

    private String username;

    private UserRole role;

    @ToString.Exclude
    private String password;

}
