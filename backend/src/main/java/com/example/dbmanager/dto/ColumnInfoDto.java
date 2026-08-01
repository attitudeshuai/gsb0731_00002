package com.example.dbmanager.dto;

/**
 * 表结构列信息（information_schema.COLUMNS）。
 */
public record ColumnInfoDto(String name, String type, boolean nullable, String defaultValue,
                            String comment, String key, String extra) {
}
