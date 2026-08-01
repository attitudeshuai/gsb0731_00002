package com.dbmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public PageResponse() {
    }

    public PageResponse(List<T> content, long totalElements, int page, int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    }

    public static <T> PageResponse<T> of(List<T> content, long totalElements, int page, int size) {
        return new PageResponse<>(content, totalElements, page, size);
    }
}
