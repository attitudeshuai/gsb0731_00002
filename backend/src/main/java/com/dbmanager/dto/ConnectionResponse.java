package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionResponse {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private Boolean hasPassword;
    private String databaseName;
    private Long groupId;
    private String color;
    private String remark;
    private Instant createdAt;
    private Instant updatedAt;
}
