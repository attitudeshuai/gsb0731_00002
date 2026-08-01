package com.example.dbmanager.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 列筛选条件。op 支持：=, !=, >, >=, <, <=, LIKE, IS NULL, IS NOT NULL。
 */
public record FilterCondition(String column, @JsonAlias("op") String operator, Object value) {
}
