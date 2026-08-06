package tgb.cryptoexchange.clientsapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.clientsapi.dto.GeneratedKeys;
import tgb.cryptoexchange.clientsapi.entity.Client;
import tgb.cryptoexchange.clientsapi.exceptions.BaseException;
import tgb.cryptoexchange.clientsapi.exceptions.FieldNotBeEmptyException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static javax.xml.crypto.dsig.SignatureMethod.HMAC_SHA256;

@Service
@Slf4j
public class ClientCredentialsService {

    private static final String PREFIX = "tgb";

    private final String masterKey;

    private final SecureRandom secureRandom = new SecureRandom();

    public ClientCredentialsService(@Value("${secrets.master-key}") String masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * Генерирует новую пару API-ключ/секрет для клиента.
     * Метод формирует составной API-ключ, включающий префикс, случайную строку и контрольную сумму CRC32.
     *
     * @param client сущность клиента, в которую будут записаны хэшированные учетные данные
     * @return {@link GeneratedKeys}, содержащий открытый API-ключ и секрет для отображения пользователю
     */
    public GeneratedKeys generateApiSecret(Client client) {
        String body = generateRandomString();
        String checksum = calculateCrc32(body);
        String rawApiKey = PREFIX + "_" + body + "_" + checksum;

        client.setApiKey(hashSha256(rawApiKey));

        String preview = PREFIX + "_" + body.substring(0, 4) + "...." + checksum.substring(checksum.length() - 4);
        client.setApiKeyPreview(preview);

        byte[] rawSecret = new byte[32];
        secureRandom.nextBytes(rawSecret);
        String rawSecretForClient = Base64.getEncoder().encodeToString(rawSecret);
        client.setSecret(encryptAesGcm(rawSecretForClient.getBytes(StandardCharsets.UTF_8)));
        return new GeneratedKeys(rawApiKey, rawSecretForClient);
    }

    /**
     * Вычисляет криптографический хэш SHA-256 для переданной строки.
     * Возвращает результат в виде строки в формате HEX.
     *
     * @param input исходная строка для хэширования
     * @return строка хэша в нижнем регистре (HEX-формат)
     * @throws BaseException если алгоритм SHA-256 не поддерживается текущей JVM
     */
    public String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BaseException("SHA-256 algorithm not available");
        }
    }

    /**
     * Расшифровывает строку секрета, защищенную алгоритмом AES-GCM.
     * Ожидает на вход Base64-строку, содержащую 12 байт IV (вектор инициализации)
     * и зашифрованный текст. Расшифровка выполняется мастер-ключом с длиной тега аутентификации 128 бит.
     *
     * @param encryptedSecret зашифрованный секрет в формате Base64 (IV + CipherText)
     * @return расшифрованный секрет
     * @throws BaseException если произошла ошибка декодирования, неверный ключ или повреждены данные (AEAD-аутентификация провалена)
     */
    public String decryptAesGcm(String encryptedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedSecret);
            ByteBuffer bb = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[12];
            bb.get(iv);
            byte[] cipherText = new byte[bb.remaining()];
            bb.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new BaseException("Failed to decrypt secret");
        }
    }

    /**
     * Зашифровывает массив байт с использованием алгоритма AES-GCM.
     * Для каждого вызова генерируется случайный 12-байтовый вектор инициализации (IV).
     * Результат формируется в виде объединенного массива [IV + CipherText] (тег 128 бит)
     * и кодируется в формат Base64.
     *
     * @param data исходные бинарные данные для шифрования
     * @return зашифрованная строка в формате Base64, содержащая IV и шифротекст
     * @throws BaseException если произошла системная ошибка шифрования или не настроен мастер-ключ
     */
    public String encryptAesGcm(byte[] data) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] cipherText = cipher.doFinal(data);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Encryption operation failed. Check master key configuration.");
            throw new BaseException("Encryption error");
        }
    }

    private String generateRandomString() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 16);
    }

    private String calculateCrc32(String input) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }

    /**
     * Генерирует цифровую подпись HMAC-SHA256 в формате Hex.
     *
     * @param data   исходные данные для подписания
     * @param secret секретный ключ клиента
     * @return строковое представление подписи в шестнадцатеричном формате (Hex)
     * @throws FieldNotBeEmptyException если data или secret равны null
     * @throws BaseException            при критических ошибках инициализации алгоритма
     */
    public String generateHmacSha256(String data, String secret) {
        if (data == null || secret == null) {
            throw new FieldNotBeEmptyException("Data and secret");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKey);

            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC algorithm not available", e);
            throw new BaseException("Failed to generate data signature.");
        }
    }

}