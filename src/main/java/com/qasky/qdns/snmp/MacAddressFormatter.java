package com.qasky.qdns.snmp;

import java.util.Locale;
import java.util.regex.Pattern;

final class MacAddressFormatter {

    private static final Pattern MAC_WITH_SEPARATOR_PATTERN =
            Pattern.compile("(?i)^[0-9a-f]{2}([:-][0-9a-f]{2}){5}$");
    private static final Pattern MAC_COMPACT_PATTERN =
            Pattern.compile("(?i)^[0-9a-f]{12}$");

    private MacAddressFormatter() {
    }

    static String format(String textValue, byte[] rawValue) {
        String normalizedText = normalizeText(textValue);
        if (normalizedText != null) {
            return normalizedText;
        }
        return formatRaw(rawValue);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (MAC_WITH_SEPARATOR_PATTERN.matcher(normalized).matches()) {
            return normalized.replace('-', ':').toUpperCase(Locale.ROOT);
        }
        if (MAC_COMPACT_PATTERN.matcher(normalized).matches()) {
            StringBuilder builder = new StringBuilder(17);
            for (int i = 0; i < normalized.length(); i += 2) {
                if (i > 0) {
                    builder.append(':');
                }
                builder.append(normalized, i, i + 2);
            }
            return builder.toString().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String formatRaw(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length * 3 - 1);
        for (int i = 0; i < value.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.ROOT, "%02X", value[i] & 0xFF));
        }
        return builder.toString();
    }
}
