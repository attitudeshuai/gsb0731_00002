package com.dbmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class TableDataRequest {

    private Integer page = 1;
    private Integer pageSize = 100;
    private String sortColumn;
    private String sortDirection;
    private List<QueryRequest.FilterCondition> filters;
}
