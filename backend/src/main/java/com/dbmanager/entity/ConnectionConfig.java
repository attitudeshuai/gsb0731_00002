package com.dbmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port = 3306;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 512)
    private String password;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "group_id")
    private Long groupId;

    @Column(length = 16)
    private String color;

    @Column(length = 512)
    private String remark;
}
