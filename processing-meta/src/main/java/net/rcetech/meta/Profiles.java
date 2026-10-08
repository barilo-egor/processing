package net.rcetech.meta;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Profiles {
    /**
     * Профили взаимодействия с merchant-details
     */
    public static final String TEST_MERCHANT_CALLBACK = "md-test-callback";
    public static final String TEST_MERCHANT_DETAILS = "md-test-details";
    public static final String NOT_TEST_MERCHANT_DETAILS = "!" + TEST_MERCHANT_DETAILS;

    /**
     * Профили взаимодействия с merchant-history
     */
    public static final String TEST_MERCHANT_HISTORY = "mh-test";
    public static final String NOT_TEST_MERCHANT_HISTORY = "!" + TEST_MERCHANT_HISTORY;
}
