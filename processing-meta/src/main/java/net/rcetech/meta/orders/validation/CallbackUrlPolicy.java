package net.rcetech.meta.orders.validation;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Требования к адресу, на который отправляются КБ клиента.
 */
public final class CallbackUrlPolicy {

    private static final int MAX_LENGTH = 2048;

    private static final String SCHEME = "https";

    private static final int PORT = 443;

    private static final List<String> FORBIDDEN_HOST_SUFFIXES = List.of(".localhost", ".local", ".internal");

    private static final Pattern TOP_LEVEL_LABEL = Pattern.compile("[a-z][a-z0-9-]*");

    private CallbackUrlPolicy() {
    }

    /**
     * Проверяет, что адрес не длиннее 2048 символов, с протоколом https и портом 443, без логина с паролем
     * и фрагмента, а хост задан доменным именем публичного сервера.
     *
     * @param url адрес КБ
     * @return {@code true}, если адрес допустим
     */
    public static boolean isAllowed(String url) {
        if (url.isEmpty() || url.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        return SCHEME.equalsIgnoreCase(uri.getScheme())
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == PORT)
                && isAllowedHost(uri.getHost());
    }

    /**
     * Проверяет, что IP-адрес принадлежит публичной сети.
     *
     * @param address IP-адрес хоста КБ
     * @return {@code true}, если адрес не локальный, не приватный, не служебный и не multicast
     */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first != 0 && !(first == 100 && second >= 64 && second <= 127);
        }
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xFE) != 0xFC;
        }
        return true;
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot <= 0 || !TOP_LEVEL_LABEL.matcher(normalized.substring(lastDot + 1)).matches()) {
            return false;
        }
        String domain = normalized;
        return FORBIDDEN_HOST_SUFFIXES.stream().noneMatch(domain::endsWith);
    }
}
