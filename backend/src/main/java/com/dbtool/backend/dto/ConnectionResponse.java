package com.dbtool.backend.dto;

import com.dbtool.backend.entity.Connection;

import java.time.Instant;

/** Connection view exposed to clients. Never includes the password. */
public class ConnectionResponse {
    public Long id;
    public String name;
    public Long groupId;
    public String host;
    public Integer port;
    public String username;
    public String databaseName;
    public String dbType;
    public boolean hasPassword;
    public Instant createdAt;
    public Instant updatedAt;

    public static ConnectionResponse from(Connection c) {
        ConnectionResponse r = new ConnectionResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.groupId = c.getGroupId();
        r.host = c.getHost();
        r.port = c.getPort();
        r.username = c.getUsername();
        r.databaseName = c.getDatabaseName();
        r.dbType = c.getDbType();
        r.hasPassword = c.getPasswordEncrypted() != null && !c.getPasswordEncrypted().isBlank();
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();
        return r;
    }
}
