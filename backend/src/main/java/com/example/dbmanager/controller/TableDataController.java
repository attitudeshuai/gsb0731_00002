package com.example.dbmanager.controller;

import com.example.dbmanager.dto.AffectedRowsResponse;
import com.example.dbmanager.dto.RowDeleteRequest;
import com.example.dbmanager.dto.RowInsertRequest;
import com.example.dbmanager.dto.RowUpdateRequest;
import com.example.dbmanager.dto.TableDataResponse;
import com.example.dbmanager.service.TableDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表数据浏览 / 编辑。导出见 ExportTaskController（异步任务式）。
 */
@RestController
@RequestMapping("/api/connections/{connId}/databases/{db}/tables/{table}")
public class TableDataController {

    private final TableDataService tableDataService;

    public TableDataController(TableDataService tableDataService) {
        this.tableDataService = tableDataService;
    }

    @GetMapping("/data")
    public TableDataResponse data(@PathVariable Long connId,
                                  @PathVariable String db,
                                  @PathVariable String table,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "100") int size,
                                  @RequestParam(required = false) String sort,
                                  @RequestParam(required = false) String order,
                                  @RequestParam(required = false) String filters) {
        return tableDataService.getData(connId, db, table, page, size, sort, order, filters);
    }

    @PostMapping("/rows")
    public ResponseEntity<AffectedRowsResponse> insert(@PathVariable Long connId,
                                                       @PathVariable String db,
                                                       @PathVariable String table,
                                                       @RequestBody RowInsertRequest request) {
        int affected = tableDataService.insertRow(connId, db, table, request.values());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AffectedRowsResponse(affected));
    }

    @PutMapping("/rows")
    public AffectedRowsResponse update(@PathVariable Long connId,
                                       @PathVariable String db,
                                       @PathVariable String table,
                                       @RequestBody RowUpdateRequest request) {
        int affected = tableDataService.updateRow(connId, db, table, request.keys(), request.values());
        return new AffectedRowsResponse(affected);
    }

    @DeleteMapping("/rows")
    public AffectedRowsResponse delete(@PathVariable Long connId,
                                       @PathVariable String db,
                                       @PathVariable String table,
                                       @RequestBody RowDeleteRequest request) {
        int affected = tableDataService.deleteRows(connId, db, table, request.keys());
        return new AffectedRowsResponse(affected);
    }
}
