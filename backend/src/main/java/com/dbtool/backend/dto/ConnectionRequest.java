package com.dbtool.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for creating/updating a connection. */
public class ConnectionRequest {

    @NotBlank
    public String name;

    public Long groupId;

    @NotBlank
    public String host;

    @NotNull
    @Min(1)
    @Max(65535)
    public Integer port = 3306;

    @NotBlank
    public String username;

    /** Plaintext password from the client; encrypted before persisting. May be null on update to keep existing. */
    public String password;

    public String databaseName;

    public String dbType = "mysql";
}
