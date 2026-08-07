package tgb.cryptoexchange.processingsupportusers.dto;

import lombok.*;
import tgb.cryptoexchange.processingsupportusers.enums.UserRole;

/**
 * @see tgb.cryptoexchange.processingsupportusers.entity.SupportUser
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
