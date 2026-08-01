package com.example.dbmanager.dto;

import java.time.Instant;

public record QueryFolderResponse(Long id, String name, Integer sortOrder,
                                  Instant createdAt, Instant updatedAt) {
}
