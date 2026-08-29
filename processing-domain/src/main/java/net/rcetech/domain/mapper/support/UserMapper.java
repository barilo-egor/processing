package net.rcetech.domain.mapper.support;

import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.meta.support.dto.UserDTO;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDTO fromEntity(final SupportUser user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .build();
    }

}
