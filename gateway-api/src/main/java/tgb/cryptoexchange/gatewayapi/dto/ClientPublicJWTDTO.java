package tgb.cryptoexchange.gatewayapi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientPublicJWTDTO {

    private String jwtKey;

}
