package net.rcetech.support.mapper;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import net.rcetech.grpc.generated.FindAllMerchantConfigsResponseGrpc;
import net.rcetech.grpc.generated.MerchantConfigResponseGrpc;
import net.rcetech.grpc.generated.UpdateMerchantConfigRequestGrpc;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;

/**
 * Маппер между DTO конфигурации мерчанта и gRPC-сообщениями.
 */
@Component
public class MerchantConfigMapper {

    /**
     * Преобразует ответ FindAll в список DTO.
     *
     * @param response gRPC-ответ со списком конфигураций
     * @return список DTO конфигураций
     */
    public List<MerchantConfigResponseDTO> merchantConfigsToList(FindAllMerchantConfigsResponseGrpc response) {
        return response.getConfigsList().stream()
                .map(this::grpcToDto)
                .toList();
    }

    /**
     * Преобразует gRPC-ответ в DTO.
     *
     * @param response gRPC-ответ конфигурации
     * @return DTO конфигурации
     */
    public MerchantConfigResponseDTO grpcToDto(MerchantConfigResponseGrpc response) {
        return new MerchantConfigResponseDTO(
                response.getId(),
                response.hasIsOn() ? response.getIsOn().getValue() : null,
                response.getMerchant().isBlank() ? null : Merchant.valueOf(response.getMerchant()),
                response.hasMaxAmount() ? response.getMaxAmount().getValue() : null,
                response.hasMinAmount() ? response.getMinAmount().getValue() : null
        );
    }

    /**
     * Собирает gRPC-запрос обновления из идентификатора и DTO с частичным обновлением.
     *
     * @param id        идентификатор конфигурации
     * @param updateDTO поля для обновления (только non-null попадают в запрос)
     * @return gRPC-запрос обновления
     */
    public UpdateMerchantConfigRequestGrpc updateDtoToGrpc(Long id, MerchantConfigUpdateDTO updateDTO) {
        UpdateMerchantConfigRequestGrpc.Builder builder = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(id);

        if (updateDTO.isOn() != null) {
            builder.setIsOn(BoolValue.of(updateDTO.isOn()));
        }
        if (updateDTO.maxAmount() != null) {
            builder.setMaxAmount(Int32Value.of(updateDTO.maxAmount()));
        }
        if (updateDTO.minAmount() != null) {
            builder.setMinAmount(Int32Value.of(updateDTO.minAmount()));
        }

        return builder.build();
    }

}
