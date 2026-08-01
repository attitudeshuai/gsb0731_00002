package com.example.dbmanager.dto;

import java.util.List;

public record TableStructureResponse(String ddl, List<ColumnInfoDto> columns,
                                     List<IndexInfoDto> indexes, Long estimatedRows) {
}
