package com.example.dbmanager.dto;

public record SavedQueryRequest(String name, String sqlText, Long folderId) {
}
