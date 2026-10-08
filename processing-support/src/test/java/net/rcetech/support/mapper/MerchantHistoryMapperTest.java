package net.rcetech.support.mapper;

import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import net.rcetech.grpc.generated.MerchantHistoryRequestGrpc;
import net.rcetech.grpc.generated.MerchantHistoryResponseDTO;
import net.rcetech.grpc.generated.MerchantHistoryResponseGrpc;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantHistoryMapperTest {

    private final String applicationName = "processing";

    private final MerchantHistoryMapper mapper = new MerchantHistoryMapper(applicationName);

    @Test
    @DisplayName("Должен корректно преобразовать пустой фильтр и unpaged пагинацию.")
    void shouldMapEmptyFilterAndUnpaged() {
        MerchantHistoryRequestGrpc request = mapper.filterToGrpc(null, Pageable.unpaged());

        assertThat(request).isNotNull();
        assertThat(request.hasPagination()).isFalse();
        assertThat(request.hasOrderId()).isFalse();
        assertThat(request.hasOperationId()).isFalse();
        assertThat(request.hasActorId()).isFalse();
        assertThat(request.hasCreatedAtFrom()).isFalse();
        assertThat(request.hasCreatedAtTo()).isFalse();
        assertThat(request.getInitiatorApp().getValue()).isEqualTo(applicationName);
        assertThat(request.hasDetails()).isFalse();
        assertThat(request.getMerchantsList()).isEmpty();
        assertThat(request.hasMerchantAmount()).isFalse();
        assertThat(request.hasRequestedAmount()).isFalse();
    }

    @Test
    @DisplayName("Должен корректно заполнить все поля gRPC-запроса из фильтра и пагинации.")
    void shouldMapFullFilterAndPagination() {
        Instant now = Instant.now();
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                "order-123",
                "op-456",
                "actor-789",
                now.minusSeconds(3600),
                now,
                "details-info",
                List.of(Merchant.ALFA_TEAM, Merchant.FIAT_CUT),
                5000,
                5500
        );

        Pageable pageable = PageRequest.of(2, 50, Sort.by(Sort.Direction.DESC, "createdAt"));

        MerchantHistoryRequestGrpc request = mapper.filterToGrpc(filter, pageable);

        assertThat(request).isNotNull();
        assertThat(request.hasPagination()).isTrue();
        assertThat(request.getPagination().getPage()).isEqualTo(2);
        assertThat(request.getPagination().getSize()).isEqualTo(50);
        assertThat(request.getPagination().getSort()).isEqualTo("createdAt,desc");

        assertThat(request.hasOrderId()).isTrue();
        assertThat(request.getOrderId().getValue()).isEqualTo("order-123");
        assertThat(request.hasOperationId()).isTrue();
        assertThat(request.getOperationId().getValue()).isEqualTo("op-456");
        assertThat(request.hasActorId()).isTrue();
        assertThat(request.getActorId().getValue()).isEqualTo("actor-789");
        assertThat(request.hasCreatedAtFrom()).isTrue();
        assertThat(request.getCreatedAtFrom().getValue()).isEqualTo(filter.createdAtFrom().toEpochMilli());
        assertThat(request.hasCreatedAtTo()).isTrue();
        assertThat(request.getCreatedAtTo().getValue()).isEqualTo(filter.createdAtTo().toEpochMilli());
        assertThat(request.hasInitiatorApp()).isTrue();
        assertThat(request.getInitiatorApp().getValue()).isEqualTo(applicationName);
        assertThat(request.hasDetails()).isTrue();
        assertThat(request.getDetails().getValue()).isEqualTo("details-info");
        assertThat(request.getMerchantsList())
                .extracting(StringValue::getValue)
                .containsExactly("ALFA_TEAM", "FIAT_CUT");
        assertThat(request.hasMerchantAmount()).isTrue();
        assertThat(request.getMerchantAmount().getValue()).isEqualTo(5000);
        assertThat(request.hasRequestedAmount()).isTrue();
        assertThat(request.getRequestedAmount().getValue()).isEqualTo(5500);
    }

    @Test
    @DisplayName("Должен корректно сформировать сортировку по возрастанию.")
    void shouldMapAscendingSort() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "amount"));
        MerchantHistoryRequestGrpc request = mapper.filterToGrpc(null, pageable);

        assertThat(request.hasPagination()).isTrue();
        assertThat(request.getPagination().getSort()).isEqualTo("amount,asc");
    }

    @Test
    @DisplayName("Должен вернуть пустой список, если ответ gRPC равен null.")
    void shouldReturnEmptyListWhenGrpcResponseIsNull() {
        var result = mapper.grpcToDtoList(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Должен вернуть null, если элемент DTO ответа gRPC равен null.")
    void shouldReturnNullWhenGrpcItemIsNull() {
        var result = mapper.grpcToDto(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Должен корректно преобразовать все поля gRPC-ответа в DTO.")
    void shouldMapGrpcResponseToDtoList() {
        long epochMillis = Instant.now().toEpochMilli();
        MerchantHistoryResponseDTO grpcDto = MerchantHistoryResponseDTO.newBuilder()
                .setOperationId(StringValue.of("op-1"))
                .setActorId(StringValue.of("act-1"))
                .setInitiatorApp(StringValue.of("proc"))
                .setCreatedAt(Int64Value.of(epochMillis))
                .setMerchant(StringValue.of("ALFA_TEAM"))
                .setMerchantOrderId(StringValue.of("m-ord-1"))
                .setRequestedAmount(Int32Value.of(1000))
                .setMerchantAmount(Int32Value.of(990))
                .setMethod(StringValue.of("CARD"))
                .setDetails(StringValue.of("card_num"))
                .build();

        MerchantHistoryResponseGrpc grpcResponse = MerchantHistoryResponseGrpc.newBuilder()
                .addResponse(grpcDto)
                .build();

        var dtoList = mapper.grpcToDtoList(grpcResponse);

        assertThat(dtoList).hasSize(1);
        var dto = dtoList.getFirst();
        assertThat(dto.operationId()).isEqualTo("op-1");
        assertThat(dto.actorId()).isEqualTo("act-1");
        assertThat(dto.createdAt()).isEqualTo(Instant.ofEpochMilli(epochMillis));
        assertThat(dto.merchant()).isEqualTo(Merchant.ALFA_TEAM);
        assertThat(dto.merchantOrderId()).isEqualTo("m-ord-1");
        assertThat(dto.requestedAmount()).isEqualTo(1000);
        assertThat(dto.merchantAmount()).isEqualTo(990);
        assertThat(dto.method()).isEqualTo("CARD");
        assertThat(dto.details()).isEqualTo("card_num");
    }

    @Test
    @DisplayName("Должен безопасно обработать неизвестный мерчант")
    void shouldHandleUnknownMerchantGracefully() {
        MerchantHistoryResponseDTO grpcDto = MerchantHistoryResponseDTO.newBuilder()
                .setMerchant(StringValue.of("UNKNOWN_SUPER_MERCHANT"))
                .build();

        var dto = mapper.grpcToDto(grpcDto);

        assertThat(dto).isNotNull();
        assertThat(dto.merchant()).isNull();
    }

}
