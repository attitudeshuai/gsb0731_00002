package com.dbmanager.service;

import com.dbmanager.entity.QueryHistory;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.QueryHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryHistoryService {

    private final QueryHistoryRepository historyRepository;

    public Page<QueryHistory> list(int page, int size, Long connectionId) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size,
                Sort.by(Sort.Direction.DESC, "executedAt"));
        if (connectionId != null) {
            return historyRepository.findByConnectionIdOrderByExecutedAtDesc(connectionId, pr);
        }
        return historyRepository.findAllByOrderByExecutedAtDesc(pr);
    }

    @Transactional
    public void delete(Long id) {
        if (!historyRepository.existsById(id)) {
            throw new ResourceNotFoundException("History not found: " + id);
        }
        historyRepository.deleteById(id);
    }

    @Transactional
    public void clear() {
        historyRepository.deleteAll();
    }
}
