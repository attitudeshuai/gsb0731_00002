package com.dbmanager.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ConnectionResponse {

    private Long id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String databaseName;
    private Long groupId;
    private String type;
    private String color;
    private String remark;
    private Instant lastConnectedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
