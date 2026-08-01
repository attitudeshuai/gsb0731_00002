package com.dbmanager.service;

import com.dbmanager.dto.GroupRequest;
import com.dbmanager.dto.GroupResponse;
import com.dbmanager.entity.ConnectionGroup;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.ConnectionGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConnectionGroupService {

    private final ConnectionGroupRepository groupRepository;

    public ConnectionGroupService(ConnectionGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<GroupResponse> listGroups() {
        return groupRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public GroupResponse getGroup(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public GroupResponse createGroup(GroupRequest request) {
        ConnectionGroup group = new ConnectionGroup();
        group.setName(request.getName());
        group.setParentId(request.getParentId());
        group.setSortOrder(request.getSortOrder());
        group.setColor(request.getColor());
        ConnectionGroup saved = groupRepository.save(group);
        return toResponse(saved);
    }

    @Transactional
    public GroupResponse updateGroup(Long id, GroupRequest request) {
        ConnectionGroup group = findOrThrow(id);
        group.setName(request.getName());
        group.setParentId(request.getParentId());
        group.setSortOrder(request.getSortOrder());
        group.setColor(request.getColor());
        ConnectionGroup saved = groupRepository.save(group);
        return toResponse(saved);
    }

    @Transactional
    public void deleteGroup(Long id) {
        ConnectionGroup group = findOrThrow(id);
        groupRepository.delete(group);
    }

    private ConnectionGroup findOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
    }

    private GroupResponse toResponse(ConnectionGroup g) {
        GroupResponse resp = new GroupResponse();
        resp.setId(g.getId());
        resp.setName(g.getName());
        resp.setParentId(g.getParentId());
        resp.setUserId(g.getUserId());
        resp.setSortOrder(g.getSortOrder());
        resp.setColor(g.getColor());
        resp.setCreatedAt(g.getCreatedAt());
        resp.setUpdatedAt(g.getUpdatedAt());
        return resp;
    }
}
