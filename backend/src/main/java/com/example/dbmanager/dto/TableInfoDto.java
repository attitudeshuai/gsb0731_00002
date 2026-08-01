package com.example.dbmanager.dto;

/**
 * 表 / 视图信息。type 为 information_schema 原值：BASE TABLE / VIEW。
 */
public record TableInfoDto(String name, String type, Long estimatedRows, String comment) {
}
