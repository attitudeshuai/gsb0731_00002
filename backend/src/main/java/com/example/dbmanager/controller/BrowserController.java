package com.example.dbmanager.controller;

import com.example.dbmanager.dto.DdlResponse;
import com.example.dbmanager.dto.TableInfoDto;
import com.example.dbmanager.dto.TableStructureResponse;
import com.example.dbmanager.service.BrowserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目标库元数据浏览。
 */
@RestController
@RequestMapping("/api/connections/{connId}")
public class BrowserController {

    private final BrowserService browserService;

    public BrowserController(BrowserService browserService) {
        this.browserService = browserService;
    }

    @GetMapping("/databases")
    public List<String> databases(@PathVariable Long connId) {
        return browserService.listDatabases(connId);
    }

    @GetMapping("/databases/{db}/tables")
    public List<TableInfoDto> tables(@PathVariable Long connId,
                                     @PathVariable String db,
                                     @RequestParam(required = false) String keyword) {
        return browserService.listTables(connId, db, keyword);
    }

    @GetMapping("/databases/{db}/tables/{table}/structure")
    public TableStructureResponse structure(@PathVariable Long connId,
                                            @PathVariable String db,
                                            @PathVariable String table) {
        return browserService.getStructure(connId, db, table);
    }

    @GetMapping("/databases/{db}/tables/{table}/ddl")
    public DdlResponse ddl(@PathVariable Long connId,
                           @PathVariable String db,
                           @PathVariable String table) {
        return new DdlResponse(browserService.getDdl(connId, db, table));
    }
}
