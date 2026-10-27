package net.rcetech.meta.orders.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class CallbackUrlValidator implements ConstraintValidator<CallbackUrl, String> {

    private static final int MAX_LENGTH = 255;

    private static final String SCHEME = "https";

    private static final int PORT = 443;

    private static final List<String> FORBIDDEN_HOST_SUFFIXES = List.of(".localhost", ".local", ".internal");

    private static final Pattern TOP_LEVEL_LABEL = Pattern.compile("[a-z][a-z0-9-]*");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        return SCHEME.equalsIgnoreCase(uri.getScheme())
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == PORT)
                && isAllowedHost(uri.getHost());
    }

    private boolean isAllowedHost(String host) {
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
