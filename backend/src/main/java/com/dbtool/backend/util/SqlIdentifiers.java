package com.dbtool.backend.util;

import com.dbtool.backend.web.ApiException;
import org.springframework.http.HttpStatus;

/** Helpers for safely embedding MySQL identifiers in dynamically built SQL. */
public final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    /**
     * Quotes a MySQL identifier with backticks, escaping embedded backticks.
     * Rejects null/blank and control characters to avoid injection via identifiers.
     */
    public static String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Identifier must not be blank");
        }
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (ch == '\u0000' || ch == '\n' || ch == '\r') {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Illegal identifier: " + identifier);
            }
        }
        return "`" + identifier.replace("`", "``") + "`";
    }
}
