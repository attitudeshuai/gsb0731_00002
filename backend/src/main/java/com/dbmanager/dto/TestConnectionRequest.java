package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestConnectionRequest {

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    private Integer port;

    @NotBlank(message = "Username is required")
    private String username;

    private String password;

    private String databaseName;
}
