package net.rcetech.domain.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LikeUtils {

    public static String contains(String term) {
        return term == null ? null : "%" + term + "%";
    }

    public static String startsWith(String term) {
        return term == null ? null : term + "%";
    }
}
