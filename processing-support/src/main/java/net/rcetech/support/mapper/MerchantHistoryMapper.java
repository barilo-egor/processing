package net.rcetech.support.mapper;

import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.grpc.generated.MerchantHistoryRequestGrpc;
import net.rcetech.grpc.generated.MerchantHistoryResponseDTO;
import net.rcetech.grpc.generated.MerchantHistoryResponseGrpc;
import net.rcetech.grpc.generated.PaginationGrpc;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Маппер между DTO истории мерчанта и gRPC-сообщениями.
 */
@Slf4j
@Component
public class MerchantHistoryMapper {

    private final String applicationName;

    public MerchantHistoryMapper(@Value("${spring.application.name:processing}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Преобразует фильтр и пагинацию в gRPC-запрос.
     *
     * @param filter   фильтр поиска
     * @param pageable параметры пагинации и сортировки
     * @return gRPC-запрос истории
     */
    public MerchantHistoryRequestGrpc filterToGrpc(MerchantHistoryFilter filter, Pageable pageable) {
        MerchantHistoryRequestGrpc.Builder builder = MerchantHistoryRequestGrpc.newBuilder();
        mapPagination(builder, pageable);
        mapFilter(builder, filter);
        builder.setInitiatorApp(StringValue.of(applicationName));
        return builder.build();
    }

    private void mapPagination(MerchantHistoryRequestGrpc.Builder builder, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return;
        }
        PaginationGrpc.Builder paginationBuilder = PaginationGrpc.newBuilder()
                .setPage(pageable.getPageNumber())
                .setSize(pageable.getPageSize());

        buildSort(pageable.getSort()).ifPresent(paginationBuilder::setSort);
        builder.setPagination(paginationBuilder);
    }

    private Optional<String> buildSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return Optional.empty();
        }
        return sort.stream().findFirst().map(order -> {
            String direction = order.isDescending() ? "desc" : "asc";
            return order.getProperty() + "," + direction;
        });
    }

    private void mapFilter(MerchantHistoryRequestGrpc.Builder builder, MerchantHistoryFilter filter) {
        if (filter == null) {
            return;
        }
        mapIdentifiers(builder, filter);
        mapDateFilters(builder, filter);
        mapDetailsAndAmounts(builder, filter);
    }

    private void mapIdentifiers(MerchantHistoryRequestGrpc.Builder builder, MerchantHistoryFilter filter) {
        if (StringUtils.isNotBlank(filter.orderId())) {
            builder.setOrderId(StringValue.of(filter.orderId()));
        }
        if (StringUtils.isNotBlank(filter.operationId())) {
            builder.setOperationId(StringValue.of(filter.operationId()));
        }
        if (StringUtils.isNotBlank(filter.actorId())) {
            builder.setActorId(StringValue.of(filter.actorId()));
        }
    }

    private void mapDateFilters(MerchantHistoryRequestGrpc.Builder builder, MerchantHistoryFilter filter) {
        if (filter.createdAtFrom() != null) {
            builder.setCreatedAtFrom(Int64Value.of(filter.createdAtFrom().toEpochMilli()));
        }
        if (filter.createdAtTo() != null) {
            builder.setCreatedAtTo(Int64Value.of(filter.createdAtTo().toEpochMilli()));
        }
    }

    private void mapDetailsAndAmounts(MerchantHistoryRequestGrpc.Builder builder, MerchantHistoryFilter filter) {
        if (StringUtils.isNotBlank(filter.details())) {
            builder.setDetails(StringValue.of(filter.details()));
        }
        mapMerchants(builder, filter.merchants());
        if (filter.amount() != null) {
            builder.setMerchantAmount(Int32Value.of(filter.amount()));
            builder.setRequestedAmount(Int32Value.of(filter.amount()));
        }
    }

    private void mapMerchants(MerchantHistoryRequestGrpc.Builder builder, List<Merchant> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return;
        }
        for (Merchant merchant : merchants) {
            if (merchant != null) {
                builder.addMerchants(StringValue.of(merchant.name()));
            }
        }
    }

    /**
     * Преобразует ответ GetHistory в список DTO.
     *
     * @param response gRPC-ответ со списком записей истории
     * @return список DTO истории мерчанта
     */
    public List<MerchantHistoryResponse> grpcToDtoList(
            MerchantHistoryResponseGrpc response) {
        if (response == null) {
            return Collections.emptyList();
        }
        return response.getResponseList().stream()
                .map(this::grpcToDto)
                .toList();
    }

    /**
     * Преобразует отдельный элемент ответа gRPC в DTO.
     *
     * @param response gRPC-элемент истории
     * @return DTO истории мерчанта
     */
    public MerchantHistoryResponse grpcToDto(MerchantHistoryResponseDTO response) {
        if (response == null) {
            return null;
        }
        return new MerchantHistoryResponse(
                response.hasOperationId() ? response.getOperationId().getValue() : null,
                response.hasActorId() ? response.getActorId().getValue() : null,
                response.hasInitiatorApp() ? response.getInitiatorApp().getValue() : null,
                response.hasCreatedAt() ? Instant.ofEpochMilli(response.getCreatedAt().getValue()) : null,
                parseMerchant(response.hasMerchant() ? response.getMerchant().getValue() : null),
                response.hasMerchantOrderId() ? response.getMerchantOrderId().getValue() : null,
                response.hasRequestedAmount() ? response.getRequestedAmount().getValue() : null,
                response.hasMerchantAmount() ? response.getMerchantAmount().getValue() : null,
                response.hasMethod() ? response.getMethod().getValue() : null,
                response.hasDetails() ? response.getDetails().getValue() : null
        );
    }

    private Merchant parseMerchant(String merchantName) {
        if (StringUtils.isBlank(merchantName)) {
            return null;
        }
        try {
            return Merchant.valueOf(merchantName);
        } catch (IllegalArgumentException e) {
            log.warn("Неизвестный мерчант: {}", merchantName);
            return null;
        }
    }

}
