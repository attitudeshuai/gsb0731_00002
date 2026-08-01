package com.example.dbmanager.dto;

import java.util.List;
import java.util.Map;

public record RowDeleteRequest(List<Map<String, Object>> keys) {
}
