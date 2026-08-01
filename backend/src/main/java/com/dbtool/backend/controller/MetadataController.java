package com.dbtool.backend.controller;

import com.dbtool.backend.dto.MetadataDto;
import com.dbtool.backend.service.MetadataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Schema browsing for a target connection. */
@RestController
@RequestMapping("/api/connections/{connectionId}")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/databases")
    public List<String> databases(@PathVariable Long connectionId) {
        return metadataService.listDatabases(connectionId);
    }

    @GetMapping("/databases/{database}/tables")
    public List<MetadataDto.TableInfo> tables(@PathVariable Long connectionId,
                                              @PathVariable String database) {
        return metadataService.listTables(connectionId, database);
    }

    @GetMapping("/databases/{database}/tables/{table}/structure")
    public MetadataDto.TableStructure structure(@PathVariable Long connectionId,
                                                @PathVariable String database,
                                                @PathVariable String table) {
        return metadataService.getTableStructure(connectionId, database, table);
    }

    @GetMapping("/databases/{database}/tables/{table}/columns")
    public List<MetadataDto.ColumnInfo> columns(@PathVariable Long connectionId,
                                                @PathVariable String database,
                                                @PathVariable String table) {
        return metadataService.listColumns(connectionId, database, table);
    }

    @GetMapping("/databases/{database}/tables/{table}/indexes")
    public List<MetadataDto.IndexInfo> indexes(@PathVariable Long connectionId,
                                               @PathVariable String database,
                                               @PathVariable String table) {
        return metadataService.listIndexes(connectionId, database, table);
    }

    @GetMapping("/databases/{database}/tables/{table}/ddl")
    public Map<String, String> ddl(@PathVariable Long connectionId,
                                   @PathVariable String database,
                                   @PathVariable String table) {
        return Map.of("ddl", metadataService.getCreateDdl(connectionId, database, table));
    }
}
