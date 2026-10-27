package net.rcetech.orders.callback;

import net.rcetech.meta.orders.validation.CallbackUrlPolicy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Проверка адреса КБ непосредственно перед отправкой.
 */
@Component
public class CallbackTargetGuard {

    /**
     * Убеждается, что все IP-адреса хоста КБ публичные.
     *
     * @param url адрес КБ
     * @throws ForbiddenCallbackUrlException если хост указывает на внутренний IP-адрес
     * @throws UnknownHostException          если хост не удалось разрешить
     */
    public void check(String url) throws UnknownHostException {
        String host = URI.create(url).getHost();
        for (InetAddress address : resolve(host)) {
            if (!CallbackUrlPolicy.isPublicAddress(address)) {
                throw new ForbiddenCallbackUrlException("хост %s указывает на внутренний адрес %s"
                        .formatted(host, address.getHostAddress()));
            }
        }
    }

    InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
