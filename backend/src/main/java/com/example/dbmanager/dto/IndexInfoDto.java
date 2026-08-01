package com.example.dbmanager.dto;

import java.util.List;

/**
 * 索引信息（information_schema.STATISTICS 聚合）。
 */
public record IndexInfoDto(String name, String type, List<String> columns, boolean unique) {
}
