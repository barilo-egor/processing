package net.rcetech.processingsupportusers.dto;

import lombok.*;
import net.rcetech.processingsupportusers.entity.SupportUser;
import net.rcetech.processingsupportusers.enums.UserRole;

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
