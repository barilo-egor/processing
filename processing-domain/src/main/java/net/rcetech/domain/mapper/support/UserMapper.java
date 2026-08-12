package net.rcetech.domain.mapper.support;

import org.springframework.stereotype.Component;
import net.rcetech.meta.support.dto.UserDTO;
import net.rcetech.domain.model.support.SupportUser;

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
