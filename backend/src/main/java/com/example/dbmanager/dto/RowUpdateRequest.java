package com.example.dbmanager.dto;

import java.util.Map;

public record RowUpdateRequest(Map<String, Object> keys, Map<String, Object> values) {
}
