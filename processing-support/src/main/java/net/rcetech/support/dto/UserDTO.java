package net.rcetech.support.dto;

import lombok.*;
import net.rcetech.support.entity.SupportUser;
import net.rcetech.support.enums.UserRole;

/**
 * @see SupportUser
 */
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
