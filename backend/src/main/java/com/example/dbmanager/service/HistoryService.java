package com.example.dbmanager.service;

import com.example.dbmanager.entity.QueryHistory;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.QueryHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

    private final QueryHistoryRepository historyRepository;

    public HistoryService(QueryHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * 倒序分页查询（executed_at 倒序）。
     */
    public Page<QueryHistory> page(Long connectionId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "executedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return connectionId == null
                ? historyRepository.findAll(pageable)
                : historyRepository.findByConnectionId(connectionId, pageable);
    }

    @Transactional
    public void delete(Long id) {
        if (!historyRepository.existsById(id)) {
            throw new NotFoundException("历史记录不存在: " + id);
        }
        historyRepository.deleteById(id);
    }

    /**
     * 清空历史（可按 connectionId 过滤）。
     */
    @Transactional
    public void clear(Long connectionId) {
        if (connectionId == null) {
            historyRepository.deleteAll();
        } else {
            historyRepository.deleteByConnectionId(connectionId);
        }
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }
}
