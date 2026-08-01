package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConnectionRequest {

    @NotBlank(message = "Connection name is required")
    private String name;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    private Integer port;

    @NotBlank(message = "Username is required")
    private String username;

    private String password;

    private String databaseName;

    private Long groupId;

    private String color;

    private String remark;
}
