package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.MyGroupItem;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListMyGroupsUseCase {

    private final MembershipRepository membershipRepository;
    private final CareGroupRepository careGroupRepository;

    @Transactional(readOnly = true)
    public List<MyGroupItem> listMyGroups(Long userId) {
        List<Membership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, CareGroup> groups = loadGroupsKeyedById(memberships);
        return toMyGroupItems(memberships, groups);
    }

    private Map<Long, CareGroup> loadGroupsKeyedById(List<Membership> memberships) {
        List<Long> ids = memberships.stream().map(Membership::getCareGroupId).toList();
        return careGroupRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CareGroup::getId, Function.identity()));
    }

    private List<MyGroupItem> toMyGroupItems(
            List<Membership> memberships, Map<Long, CareGroup> groups) {
        return memberships.stream()
                .map(m -> new MyGroupItem(
                        m.getCareGroupId(),
                        nameOrUnknown(groups.get(m.getCareGroupId())),
                        m.getRole().name()))
                .toList();
    }

    private String nameOrUnknown(CareGroup group) {
        return group == null ? "(unknown)" : group.getName();
    }
}
