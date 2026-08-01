package com.dbmanager.service;

import com.dbmanager.dto.HistoryResponse;
import com.dbmanager.dto.PageResponse;
import com.dbmanager.entity.QueryHistory;
import com.dbmanager.repository.QueryHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryHistoryService {

    private final QueryHistoryRepository queryHistoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public QueryHistoryService(QueryHistoryRepository queryHistoryRepository) {
        this.queryHistoryRepository = queryHistoryRepository;
    }

    public PageResponse<HistoryResponse> getHistory(Long connectionId, String keyword, int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<QueryHistory> query = cb.createQuery(QueryHistory.class);
        Root<QueryHistory> root = query.from(QueryHistory.class);

        List<Predicate> predicates = new ArrayList<>();

        if (connectionId != null) {
            predicates.add(cb.equal(root.get("connectionId"), connectionId));
        }

        if (keyword != null && !keyword.isBlank()) {
            predicates.add(cb.like(cb.lower(root.get("sqlText")), "%" + keyword.toLowerCase() + "%"));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("executedAt")));

        List<QueryHistory> all = entityManager.createQuery(query).getResultList();
        long total = all.size();

        int offset = page * size;
        List<QueryHistory> paged = all.stream()
                .skip(offset)
                .limit(size)
                .toList();

        List<HistoryResponse> content = paged.stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.of(content, total, page, size);
    }

    @Transactional
    public void clearHistory() {
        queryHistoryRepository.deleteAll();
    }

    @Transactional
    public QueryHistory saveHistory(QueryHistory history) {
        return queryHistoryRepository.save(history);
    }

    private HistoryResponse toResponse(QueryHistory h) {
        HistoryResponse resp = new HistoryResponse();
        resp.setId(h.getId());
        resp.setConnectionId(h.getConnectionId());
        resp.setSqlText(h.getSqlText());
        resp.setExecutionTimeMs(h.getExecutionTimeMs());
        resp.setRowCount(h.getRowCount());
        resp.setSuccess(h.isSuccess());
        resp.setErrorMessage(h.getErrorMessage());
        resp.setExecutedAt(h.getExecutedAt());
        resp.setUserId(h.getUserId());
        resp.setCreatedAt(h.getCreatedAt());
        resp.setUpdatedAt(h.getUpdatedAt());
        return resp;
    }
}
