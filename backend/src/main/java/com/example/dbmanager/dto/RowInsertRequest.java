package com.example.dbmanager.dto;

import java.util.Map;

public record RowInsertRequest(Map<String, Object> values) {
}
