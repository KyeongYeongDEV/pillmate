package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.MyGroupItem;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("ListMyGroupsUseCase — 내가 속한 그룹 목록")
@ExtendWith(MockitoExtension.class)
class ListMyGroupsUseCaseTest {

    @Mock MembershipRepository membershipRepository;
    @Mock CareGroupRepository careGroupRepository;
    @InjectMocks ListMyGroupsUseCase sut;

    @Test
    @DisplayName("멤버십이 있으면 groupId/name/role 묶여 반환된다")
    void list_returnsAllMyGroups() {
        given(membershipRepository.findByUserId(1L)).willReturn(List.of(
                Membership.of(10L, 1L, MemberRole.ADMIN, null),
                Membership.of(11L, 1L, MemberRole.PATIENT, 2L)));
        CareGroup g10 = CareGroup.create("우리 가족", 1L);
        CareGroup g11 = CareGroup.create("친구 그룹", 2L);
        setId(g10, 10L);
        setId(g11, 11L);
        given(careGroupRepository.findAllById(List.of(10L, 11L)))
                .willReturn(List.of(g10, g11));

        List<MyGroupItem> result = sut.listMyGroups(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).groupId()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("우리 가족");
        assertThat(result.get(0).role()).isEqualTo("ADMIN");
        assertThat(result.get(1).role()).isEqualTo("PATIENT");
    }

    @Test
    @DisplayName("멤버십 없으면 빈 리스트")
    void list_whenNoMembership_returnsEmpty() {
        given(membershipRepository.findByUserId(99L)).willReturn(List.of());

        assertThat(sut.listMyGroups(99L)).isEmpty();
    }

    private static void setId(CareGroup group, Long id) {
        try {
            var field = CareGroup.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(group, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
