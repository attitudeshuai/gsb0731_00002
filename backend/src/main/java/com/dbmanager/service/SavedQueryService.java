package com.dbmanager.service;

import com.dbmanager.dto.SavedQueryRequest;
import com.dbmanager.dto.SavedQueryResponse;
import com.dbmanager.entity.SavedQuery;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.SavedQueryRepository;
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
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SavedQueryService(SavedQueryRepository savedQueryRepository) {
        this.savedQueryRepository = savedQueryRepository;
    }

    public List<SavedQueryResponse> listSavedQueries(Long folderId, String keyword) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SavedQuery> query = cb.createQuery(SavedQuery.class);
        Root<SavedQuery> root = query.from(SavedQuery.class);

        List<Predicate> predicates = new ArrayList<>();

        if (folderId != null) {
            predicates.add(cb.equal(root.get("folderId"), folderId));
        }

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
            Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
            predicates.add(cb.or(nameLike, descLike));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("updatedAt")));

        return entityManager.createQuery(query).getResultList().stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedQueryResponse getSavedQuery(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SavedQueryResponse createSavedQuery(SavedQueryRequest request) {
        SavedQuery savedQuery = new SavedQuery();
        savedQuery.setName(request.getName());
        savedQuery.setSqlText(request.getSqlText());
        savedQuery.setFolderId(request.getFolderId());
        savedQuery.setConnectionId(request.getConnectionId());
        savedQuery.setDescription(request.getDescription());
        savedQuery.setTags(request.getTags());
        SavedQuery saved = savedQueryRepository.save(savedQuery);
        return toResponse(saved);
    }

    @Transactional
    public SavedQueryResponse updateSavedQuery(Long id, SavedQueryRequest request) {
        SavedQuery savedQuery = findOrThrow(id);
        savedQuery.setName(request.getName());
        savedQuery.setSqlText(request.getSqlText());
        savedQuery.setFolderId(request.getFolderId());
        savedQuery.setConnectionId(request.getConnectionId());
        savedQuery.setDescription(request.getDescription());
        savedQuery.setTags(request.getTags());
        SavedQuery saved = savedQueryRepository.save(savedQuery);
        return toResponse(saved);
    }

    @Transactional
    public void deleteSavedQuery(Long id) {
        SavedQuery savedQuery = findOrThrow(id);
        savedQueryRepository.delete(savedQuery);
    }

    private SavedQuery findOrThrow(Long id) {
        return savedQueryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SavedQuery", "id", id));
    }

    private SavedQueryResponse toResponse(SavedQuery q) {
        SavedQueryResponse resp = new SavedQueryResponse();
        resp.setId(q.getId());
        resp.setName(q.getName());
        resp.setSqlText(q.getSqlText());
        resp.setFolderId(q.getFolderId());
        resp.setConnectionId(q.getConnectionId());
        resp.setUserId(q.getUserId());
        resp.setDescription(q.getDescription());
        resp.setTags(q.getTags());
        resp.setCreatedAt(q.getCreatedAt());
        resp.setUpdatedAt(q.getUpdatedAt());
        return resp;
    }
}
