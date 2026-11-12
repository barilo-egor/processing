package net.rcetech.meta;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Profiles {
    public static final String DEV = "dev";

    /**
     * Профили взаимодействия с merchant-details
     */

    /**
     * Профили взаимодействия с merchant-history
     */
    public static final String TEST_MERCHANT_HISTORY = "mh-test";
    public static final String NOT_TEST_MERCHANT_HISTORY = "!" + TEST_MERCHANT_HISTORY;
}
