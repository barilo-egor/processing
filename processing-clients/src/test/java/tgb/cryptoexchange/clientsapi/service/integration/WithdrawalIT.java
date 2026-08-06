package tgb.cryptoexchange.clientsapi.service.integration;

import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.google.rpc.BadRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;
import tgb.cryptoexchange.clientsapi.entity.Client;
import tgb.cryptoexchange.clientsapi.entity.WithdrawalRequest;
import tgb.cryptoexchange.clientsapi.enums.ClientStatus;
import tgb.cryptoexchange.clientsapi.enums.WithdrawalRequestStatus;
import tgb.cryptoexchange.clientsapi.repository.ClientRepository;
import tgb.cryptoexchange.grpc.generated.CreateWithdrawalRequestGrpc;
import tgb.cryptoexchange.grpc.generated.UpdateWithdrawalRequestGrpc;
import tgb.cryptoexchange.grpc.generated.WithdrawalRequestServiceGrpc;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

class WithdrawalIT extends BaseIntegrationTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Value("${kafka.topic.api-clients.receive}")
    private String kafkaTopic;

    private WithdrawalRequestServiceGrpc.WithdrawalRequestServiceBlockingStub blockingStub;

    @BeforeEach
    void setup() {
        blockingStub = WithdrawalRequestServiceGrpc.newBlockingStub(channel);
    }

    @Test
    @DisplayName("Создание заявки: успех при существующем клиенте")
    void createWithdrawal_Success() {
        Client client = clientRepository.save(Client.builder()
                .username("withdrawal_user")
                .password("hash")
                .apiKey("key")
                .apiKeyPreview("pre")
                .secret("secret")
                .status(ClientStatus.ACTIVE)
                .registeredAt(Instant.now())
                .orderTimeoutSeconds(900)
                .build());

        Long existingClientId = client.getId();

        var request = CreateWithdrawalRequestGrpc.newBuilder()
                .setClientId(existingClientId)
                .setAmount(1000)
                .setWallet("TGB-WALLET-777")
                .setComment(StringValue.of("Urgent cashout"))
                .build();

        blockingStub.createWithdrawalRequest(request);

        var savedRequest = withdrawalRequestRepository.findAll().getFirst();
        assertThat(savedRequest.getClientId()).isEqualTo(existingClientId);
        assertThat(savedRequest.getAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Сохранение в БД -> отправка в Kafka")
    void createWithdrawal_ShouldPropagateToKafkaAfterCommit() {
        Client client = clientRepository.save(Client.builder()
                .username("kafka_test_user").password("pass").status(ClientStatus.ACTIVE)
                .apiKey("key").apiKeyPreview("pre").secret("sec").registeredAt(Instant.now()).orderTimeoutSeconds(900)
                .build());

        var request = CreateWithdrawalRequestGrpc.newBuilder()
                .setClientId(client.getId())
                .setAmount(5000)
                .setWallet("WALLET-123")
                .setComment(StringValue.of("Check Kafka"))
                .build();

        blockingStub.createWithdrawalRequest(request);

        var dbRequest = withdrawalRequestRepository.findAll().stream()
                .filter(r -> r.getClientId().equals(client.getId()))
                .findFirst().orElseThrow();
        assertThat(dbRequest.getAmount()).isEqualTo(5000);

        WithdrawalRequestDTO event = consumeEvent(kafkaTopic);

        assertThat(event).isNotNull();
        assertThat(event.getClientId()).isEqualTo(client.getId());
        assertThat(event.getAmount()).isEqualTo(5000);
        assertThat(event.getWallet()).isEqualTo("WALLET-123");
    }

    private WithdrawalRequestDTO consumeEvent(String topic) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        StringDeserializer keyDeserializer = new StringDeserializer();

        JacksonJsonDeserializer<WithdrawalRequestDTO> valueDeserializer = new JacksonJsonDeserializer<>(
                WithdrawalRequestDTO.class);
        valueDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, WithdrawalRequestDTO> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, keyDeserializer, valueDeserializer);

        try (Consumer<String, WithdrawalRequestDTO> consumer = consumerFactory.createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, WithdrawalRequestDTO> records = consumer.poll(Duration.ofSeconds(10));
            if (records.isEmpty()) {
                return null;
            }
            return records.iterator().next().value();
        }
    }

    @Test
    @DisplayName("Успешное обновление всех полей")
    void updateWithdrawal_Success() {
        Client client = clientRepository.save(Client.builder()
                .username("update_user").password("pass").status(ClientStatus.ACTIVE)
                .apiKey("key").apiKeyPreview("pre").secret("sec").registeredAt(Instant.now()).orderTimeoutSeconds(900)
                .build());

        WithdrawalRequest original = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .clientId(client.getId())
                .amount(1000)
                .wallet("OLD_WALLET")
                .comment("OLD_COMMENT")
                .status(WithdrawalRequestStatus.NEW)
                .build());

        String newWallet = "NEW_WALLET_ADDRESS_123";
        String newComment = "Updated by admin";

        var request = UpdateWithdrawalRequestGrpc.newBuilder()
                .setId(Int64Value.of(original.getId()))
                .setWallet(StringValue.of(newWallet))
                .setComment(StringValue.of(newComment))
                .build();

        blockingStub.updateWithdrawalRequest(request);

        WithdrawalRequest updated = withdrawalRequestRepository.findById(original.getId())
                .orElseThrow(() -> new AssertionError("Заявка не найдена после обновления"));

        assertThat(updated.getWallet()).isEqualTo(newWallet);
        assertThat(updated.getComment()).isEqualTo(newComment);
        assertThat(updated.getClientId()).isEqualTo(client.getId());
        assertThat(updated.getAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Передача пустых StringValue должна очищать поля")
    void updateWithdrawal_EmptyValues_ShouldUpdateAsEmpty() {
        Client client = clientRepository.save(
                Client.builder().username("clear_user").password("p").status(ClientStatus.ACTIVE)
                        .apiKey("k").apiKeyPreview("p").secret("s").registeredAt(Instant.now()).orderTimeoutSeconds(900)
                        .build());

        WithdrawalRequest original = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .clientId(client.getId()).amount(500).wallet("TO_BE_EMPTY").comment("TO_BE_EMPTY")
                .status(WithdrawalRequestStatus.NEW).build());

        var request = UpdateWithdrawalRequestGrpc.newBuilder()
                .setId(Int64Value.of(original.getId()))
                .setWallet(StringValue.of(""))
                .setComment(StringValue.of(""))
                .build();

        blockingStub.updateWithdrawalRequest(request);

        WithdrawalRequest updated = withdrawalRequestRepository.findById(original.getId()).get();
        assertThat(updated.getWallet()).isEmpty();
        assertThat(updated.getComment()).isEmpty();
    }

    @Test
    @DisplayName("Ошибка при несуществующем ID")
    void updateWithdrawal_NotFound_ThrowsException() {
        var request = UpdateWithdrawalRequestGrpc.newBuilder()
                .setId(Int64Value.of(999999L))
                .setWallet(StringValue.of("ANY"))
                .setComment(StringValue.of("ANY"))
                .build();

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            blockingStub.updateWithdrawalRequest(request);
        });

        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);

        com.google.rpc.Status status = StatusProto.fromThrowable(exception);
        BadRequest badRequest = null;
        try {
            badRequest = status.getDetails(0).unpack(BadRequest.class);
        } catch (InvalidProtocolBufferException e) {
            fail("Не удалось распаковать детали ошибки");
        }

        assertThat(badRequest.getFieldViolations(0).getField()).isEqualTo("999999");
        assertThat(badRequest.getFieldViolations(0).getDescription()).isEqualTo(
                "Record not found for the provided ID.");
    }

}
