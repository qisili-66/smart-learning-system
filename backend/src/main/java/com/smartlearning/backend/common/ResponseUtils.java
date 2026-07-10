package com.smartlearning.backend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ResponseUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ResponseUtils() {
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    public static String format(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
