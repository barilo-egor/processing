package net.rcetech.processingsupportusers.mapper;

import org.springframework.stereotype.Component;
import net.rcetech.processingsupportusers.dto.UserDTO;
import net.rcetech.processingsupportusers.entity.SupportUser;

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
