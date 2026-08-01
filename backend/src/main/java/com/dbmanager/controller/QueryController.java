package com.dbmanager.controller;

import com.dbmanager.dto.*;
import com.dbmanager.service.QueryCancelRegistry;
import com.dbmanager.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;
    private final QueryCancelRegistry cancelRegistry;

    @PostMapping("/execute")
    public ApiResponse<QueryResult> execute(
            @Valid @RequestBody QueryRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (request.getPage() == null) request.setPage(1);
        if (request.getPageSize() == null) request.setPageSize(100);
        return ApiResponse.ok(queryService.executeQuery(request, requestId));
    }

    @PostMapping("/execute-selected")
    public ApiResponse<QueryResult> executeSelected(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Long connectionId = Long.valueOf(body.get("connectionId").toString());
        String database = body.get("databaseName") != null ? body.get("databaseName").toString() : null;
        String sql = body.get("sql") != null ? body.get("sql").toString() : "";
        return ApiResponse.ok(queryService.executeSelected(connectionId, database, sql, requestId));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancel(@RequestBody Map<String, String> body) {
        String requestId = body.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("requestId is required"));
        }
        boolean cancelled = cancelRegistry.cancel(requestId);
        return ResponseEntity.ok(ApiResponse.ok(
                cancelled ? "Cancelled" : "No running query found",
                Map.of("cancelled", cancelled)));
    }

    @PostMapping("/{connectionId}/databases/{database}/tables/{table}/data")
    public ApiResponse<TableDataResponse> getTableData(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table,
            @RequestBody(required = false) TableDataRequest request) {
        if (request == null) {
            request = new TableDataRequest();
        }
        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 100;
        return ApiResponse.ok(queryService.getTableData(
                connectionId, database, table, page, pageSize,
                request.getSortColumn(), request.getSortDirection(), request.getFilters()));
    }

    @PutMapping("/{connectionId}/databases/{database}/tables/{table}/rows")
    public ApiResponse<Map<String, Long>> updateRow(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table,
            @RequestBody RowUpdateRequest request) {
        long affected = queryService.updateRow(connectionId, database, table,
                request.getPrimaryKey(), request.getData());
        return ApiResponse.ok("Row updated", Map.of("affectedRows", affected));
    }

    @PostMapping("/{connectionId}/databases/{database}/tables/{table}/rows")
    public ApiResponse<Map<String, Long>> insertRow(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table,
            @RequestBody RowUpdateRequest request) {
        long affected = queryService.insertRow(connectionId, database, table, request.getNewRow());
        return ApiResponse.ok("Row inserted", Map.of("affectedRows", affected));
    }

    @DeleteMapping("/{connectionId}/databases/{database}/tables/{table}/rows")
    public ApiResponse<Map<String, Long>> deleteRow(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table,
            @RequestBody RowUpdateRequest request) {
        long affected = queryService.deleteRow(connectionId, database, table, request.getPrimaryKey());
        return ApiResponse.ok("Row deleted", Map.of("affectedRows", affected));
    }
}
