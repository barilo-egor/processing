package net.rcetech.domain.mapping.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import org.mapstruct.*;

@Mapper(injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ClientMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateNotNull(UpdateClientDTO clientDTO, @MappingTarget Client client);
}
