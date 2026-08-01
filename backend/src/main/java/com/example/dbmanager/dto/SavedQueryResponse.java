package com.example.dbmanager.dto;

import java.time.Instant;

public record SavedQueryResponse(Long id, Long folderId, String name, String sqlText,
                                 Instant createdAt, Instant updatedAt) {
}
