package tgb.cryptoexchange.processingsupportusers.mapper;

import org.springframework.stereotype.Component;
import tgb.cryptoexchange.processingsupportusers.dto.UserDTO;
import tgb.cryptoexchange.processingsupportusers.entity.SupportUser;

@Component
public class UserMapper {

    public UserDTO fromEntity(final SupportUser user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .role(user.getRole())
                .build();
    }

}
