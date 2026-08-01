package com.dbtool.backend.controller;

import com.dbtool.backend.dto.QueryResult;
import com.dbtool.backend.dto.TableDataDto;
import com.dbtool.backend.service.RunningQueryRegistry;
import com.dbtool.backend.service.SqlExecutionService;
import com.dbtool.backend.service.TableDataService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** SQL execution and table-data browsing/editing for a connection. */
@RestController
@RequestMapping("/api/connections/{connectionId}")
public class QueryController {

    private final SqlExecutionService sqlExecutionService;
    private final TableDataService tableDataService;
    private final RunningQueryRegistry runningQueryRegistry;

    public QueryController(SqlExecutionService sqlExecutionService,
                           TableDataService tableDataService,
                           RunningQueryRegistry runningQueryRegistry) {
        this.sqlExecutionService = sqlExecutionService;
        this.tableDataService = tableDataService;
        this.runningQueryRegistry = runningQueryRegistry;
    }

    public static class ExecuteRequest {
        @NotBlank
        public String sql;
        /** Optional client-generated token used to cancel this query mid-flight. */
        public String queryToken;
    }

    /** Execute arbitrary SQL (records history). */
    @PostMapping("/execute")
    public QueryResult execute(@PathVariable Long connectionId, @RequestBody ExecuteRequest req) {
        return sqlExecutionService.execute(connectionId, req.sql, req.queryToken);
    }

    /** Cancel a running query by its token; the DB statement is actually cancelled. */
    @PostMapping("/queries/{queryToken}/cancel")
    public Map<String, Object> cancel(@PathVariable Long connectionId,
                                      @PathVariable String queryToken) {
        boolean cancelled = runningQueryRegistry.cancel(queryToken);
        return Map.of("cancelled", cancelled);
    }

    /** Browse table data with paging / sort / filter. */
    @PostMapping("/table-data")
    public TableDataDto.PageResponse browse(@PathVariable Long connectionId,
                                            @RequestBody TableDataDto.PageRequest req) {
        return tableDataService.browse(connectionId, req);
    }

    /**
     * Preview the exact UPDATE/INSERT/DELETE statements that a save would run,
     * WITHOUT executing anything. The client shows these for confirmation.
     */
    @PostMapping("/table-data/preview")
    public TableDataDto.SaveResponse preview(@PathVariable Long connectionId,
                                             @RequestBody TableDataDto.SaveRequest req) {
        return tableDataService.preview(connectionId, req);
    }

    /** Apply row insert / update / delete changes (single target-db transaction). */
    @PostMapping("/table-data/save")
    public TableDataDto.SaveResponse save(@PathVariable Long connectionId,
                                          @RequestBody TableDataDto.SaveRequest req) {
        return tableDataService.save(connectionId, req);
    }
}
