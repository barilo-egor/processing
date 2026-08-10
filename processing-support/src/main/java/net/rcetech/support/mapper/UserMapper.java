package net.rcetech.support.mapper;

import org.springframework.stereotype.Component;
import net.rcetech.support.dto.UserDTO;
import net.rcetech.support.entity.SupportUser;

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
