package com.example.dbmanager.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SQL 工具：标识符安全引用、多语句拆分、语句类型判定。
 */
public final class SqlUtils {

    private static final Set<String> READ_KEYWORDS =
            Set.of("SELECT", "SHOW", "EXPLAIN", "DESC", "DESCRIBE", "WITH");

    private SqlUtils() {
    }

    /**
     * 用反引号包裹标识符，并将标识符内部的反引号转义为两个反引号。
     */
    public static String quoteIdent(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 生成 `db`.`table` 形式的全限定表名。
     */
    public static String tableRef(String database, String table) {
        return quoteIdent(database) + "." + quoteIdent(table);
    }

    /**
     * 按分号拆分多条 SQL，忽略字符串字面量（单/双引号、反引号）和注释（--、#、块注释）中的分号。
     */
    public static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = i + 1 < len ? sql.charAt(i + 1) : '\0';
            if (inLineComment) {
                current.append(c);
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingle || inDouble) {
                current.append(c);
                if (c == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                } else if ((inSingle && c == '\'') || (inDouble && c == '"')) {
                    inSingle = false;
                    inDouble = false;
                }
                continue;
            }
            if (inBacktick) {
                current.append(c);
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }
            if (c == '-' && next == '-') {
                inLineComment = true;
                current.append(c);
                continue;
            }
            if (c == '#') {
                inLineComment = true;
                current.append(c);
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                current.append(c);
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                current.append(c);
                continue;
            }
            if (c == '"') {
                inDouble = true;
                current.append(c);
                continue;
            }
            if (c == '`') {
                inBacktick = true;
                current.append(c);
                continue;
            }
            if (c == ';') {
                addStatement(statements, current);
                continue;
            }
            current.append(c);
        }
        addStatement(statements, current);
        return statements;
    }

    /**
     * 判断语句是否返回结果集（SELECT/SHOW/EXPLAIN/DESC/DESCRIBE/WITH）。
     */
    public static boolean isReadQuery(String sql) {
        return READ_KEYWORDS.contains(firstKeyword(sql));
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
        current.setLength(0);
    }

    private static String firstKeyword(String sql) {
        int i = 0;
        int len = sql.length();
        // 跳过前导空白与注释
        while (i < len) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                int nl = sql.indexOf('\n', i);
                i = nl < 0 ? len : nl + 1;
                continue;
            }
            if (c == '#') {
                int nl = sql.indexOf('\n', i);
                i = nl < 0 ? len : nl + 1;
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? len : end + 2;
                continue;
            }
            break;
        }
        StringBuilder keyword = new StringBuilder();
        while (i < len && Character.isLetter(sql.charAt(i))) {
            keyword.append(sql.charAt(i));
            i++;
        }
        return keyword.toString().toUpperCase(Locale.ROOT);
    }
}
