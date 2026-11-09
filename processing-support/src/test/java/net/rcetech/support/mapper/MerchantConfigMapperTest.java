package net.rcetech.support.mapper;

import net.rcetech.grpc.generated.UpdateApiMerchantConfigItemGrpc;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantConfigMapperTest {

    private final MerchantConfigMapper mapper = new MerchantConfigMapper();

    @ParameterizedTest(name = "id={0}, merchantOrder={1}, swap={2}")
    @CsvSource({
            "1, 3, true",
            "7, 1, false",
            "12, 5, true"
    })
    @DisplayName("Должен передать в gRPC-запрос новую очерёдность и признак обмена местами.")
    void updateDtoToGrpc_shouldMapOrderAndSwap(long id, int merchantOrder, boolean swap) {
        UpdateApiMerchantConfigItemGrpc grpc = mapper.updateDtoToGrpc(
                id, new MerchantConfigUpdateDTO(null, null, null, merchantOrder, swap));

        assertThat(grpc.getId()).isEqualTo(id);
        assertThat(grpc.getNewOrder().getValue()).isEqualTo(merchantOrder);
        assertThat(grpc.hasSwap()).isTrue();
        assertThat(grpc.getSwap().getValue()).isEqualTo(swap);
    }

    @ParameterizedTest(name = "id={0}, merchantOrder={1}")
    @CsvSource({
            "2, 4",
            "9, 1"
    })
    @DisplayName("Не должен заполнять признак обмена местами, если он не передан.")
    void updateDtoToGrpc_shouldNotSetSwapIfNull(long id, int merchantOrder) {
        UpdateApiMerchantConfigItemGrpc grpc = mapper.updateDtoToGrpc(
                id, new MerchantConfigUpdateDTO(null, null, null, merchantOrder, null));

        assertThat(grpc.getNewOrder().getValue()).isEqualTo(merchantOrder);
        assertThat(grpc.hasSwap()).isFalse();
    }
}
