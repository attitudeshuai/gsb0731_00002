package com.example.dbmanager.service;

import com.example.dbmanager.dto.ConnectionGroupRequest;
import com.example.dbmanager.dto.ConnectionGroupResponse;
import com.example.dbmanager.entity.ConnectionGroup;
import com.example.dbmanager.exception.BadRequestException;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.ConnectionGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConnectionGroupService {

    private final ConnectionGroupRepository groupRepository;

    public ConnectionGroupService(ConnectionGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<ConnectionGroupResponse> list() {
        return groupRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ConnectionGroupResponse create(ConnectionGroupRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("分组名称不能为空");
        }
        ConnectionGroup group = new ConnectionGroup();
        group.setName(request.name().trim());
        group.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public ConnectionGroupResponse update(Long id, ConnectionGroupRequest request) {
        ConnectionGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("分组不存在: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            group.setName(request.name().trim());
        }
        if (request.sortOrder() != null) {
            group.setSortOrder(request.sortOrder());
        }
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public void delete(Long id) {
        if (!groupRepository.existsById(id)) {
            throw new NotFoundException("分组不存在: " + id);
        }
        // connections.group_id 外键 ON DELETE SET NULL，由数据库自动解绑
        groupRepository.deleteById(id);
    }

    private ConnectionGroupResponse toResponse(ConnectionGroup group) {
        return new ConnectionGroupResponse(group.getId(), group.getName(), group.getSortOrder(),
                group.getCreatedAt(), group.getUpdatedAt());
    }
}
