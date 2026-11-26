package com.xsframework.common;

import java.util.UUID;

public final class Ids {
    private Ids() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String businessCode(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        return prefix + raw;
    }
}
