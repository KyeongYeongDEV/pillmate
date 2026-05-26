package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCareGroupUseCase {

    private final CareGroupRepository careGroupRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeRepository inviteCodeRepository;

    @Transactional
    public CreateGroupResponse create(String name, Long creatorUserId) {
        CareGroup group = careGroupRepository.save(CareGroup.create(name, creatorUserId));
        membershipRepository.save(Membership.of(group.getId(), creatorUserId, MemberRole.ADMIN, null));
        InviteCode code = inviteCodeRepository.save(InviteCode.generate(group.getId(), creatorUserId));
        return CreateGroupResponse.of(group, code.getCode());
    }
}
