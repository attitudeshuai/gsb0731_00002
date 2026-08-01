package com.example.dbmanager.util;

import java.util.Base64;

/**
 * 结果集值转换：byte[] → Base64，日期时间 → ISO 字符串，BigDecimal/BigInteger 保持原样。
 */
public final class ValueMapper {

    private ValueMapper() {
    }

    /**
     * 将 JDBC 读取的值转换为 JSON 友好的形式。
     */
    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        return value;
    }

    /**
     * 转换为纯文本（CSV 用），null → null。
     */
    public static String stringify(Object value) {
        Object normalized = normalize(value);
        return normalized == null ? null : normalized.toString();
    }

    /**
     * 转换为 SQL 字面量（导出 INSERT 语句用）。
     */
    public static String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder("0x");
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return quote(timestamp.toLocalDateTime().toString().replace('T', ' '));
        }
        if (value instanceof java.sql.Date date) {
            return quote(date.toLocalDate().toString());
        }
        if (value instanceof java.sql.Time time) {
            return quote(time.toLocalTime().toString());
        }
        return quote(value.toString());
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
