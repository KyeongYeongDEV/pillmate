package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.dto.MyGroupSummary;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@DisplayName("ListMyGroupsUseCase — MyGroupSummary 확장 응답")
@ExtendWith(MockitoExtension.class)
class ListMyGroupsUseCaseTest {

    @Mock MembershipRepository membershipRepository;
    @Mock CareGroupRepository careGroupRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityFeedRepository activityFeedRepository;
    @InjectMocks ListMyGroupsUseCase sut;

    @Test
    @DisplayName("멤버십이 있으면 MyGroupSummary 묶여 반환 (memberCount/membersPreview/pinned 포함)")
    void list_returnsAllMyGroups() {
        Membership my10 = Membership.of(10L, 1L, MemberRole.ADMIN, null);
        my10.pin();
        Membership my11 = Membership.of(11L, 1L, MemberRole.PATIENT, 2L);
        given(membershipRepository.findByUserId(1L)).willReturn(List.of(my10, my11));

        CareGroup g10 = CareGroup.create("우리 가족", 1L);
        CareGroup g11 = CareGroup.create("친구 그룹", 2L);
        setId(g10, 10L);
        setId(g11, 11L);
        given(careGroupRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(g10, g11));

        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(my10,
                Membership.of(10L, 2L, MemberRole.PATIENT, 1L)));
        given(membershipRepository.findByCareGroupId(11L)).willReturn(List.of(my11));
        given(userRepository.findById(anyLong())).willReturn(Optional.of(User.dummy("멤버")));
        given(activityFeedRepository.findByActorSince(anyLong(), any(), anyInt())).willReturn(List.of());

        List<MyGroupSummary> result = sut.listMyGroups(1L);

        assertThat(result).hasSize(2);
        MyGroupSummary first = result.get(0);
        assertThat(first.groupId()).isEqualTo(10L);
        assertThat(first.name()).isEqualTo("우리 가족");
        assertThat(first.role()).isEqualTo("ADMIN");
        assertThat(first.memberCount()).isEqualTo(2);
        assertThat(first.membersPreview()).hasSize(2);
        assertThat(first.pinned()).isTrue();
        assertThat(first.unreadCount()).isZero();

        assertThat(result.get(1).pinned()).isFalse();
        assertThat(result.get(1).memberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("membersPreview 는 userId 오름차순 상위 N 명 — 목록 색이 상세 색과 어긋나지 않게")
    void list_membersPreviewSortedByUserIdAscending() {
        Membership my20 = Membership.of(20L, 1L, MemberRole.ADMIN, null);
        given(membershipRepository.findByUserId(1L)).willReturn(List.of(my20));
        CareGroup g20 = CareGroup.create("정렬 확인", 1L);
        setId(g20, 20L);
        given(careGroupRepository.findAllById(List.of(20L))).willReturn(List.of(g20));
        // 저장소가 임의 순서(9, 3, 7, 1)로 돌려줘도 미리보기는 1, 3, 7 이어야 한다.
        given(membershipRepository.findByCareGroupId(20L)).willReturn(List.of(
                Membership.of(20L, 9L, MemberRole.PATIENT, 1L),
                Membership.of(20L, 3L, MemberRole.GUARDIAN, 1L),
                Membership.of(20L, 7L, MemberRole.PATIENT, 1L),
                Membership.of(20L, 1L, MemberRole.ADMIN, null)));
        given(userRepository.findById(anyLong())).willReturn(Optional.of(User.dummy("멤버")));
        given(activityFeedRepository.findByActorSince(anyLong(), any(), anyInt())).willReturn(List.of());

        MyGroupSummary summary = sut.listMyGroups(1L).get(0);

        assertThat(summary.membersPreview()).extracting(MyGroupSummary.MemberPreview::userId)
                .containsExactly(1L, 3L, 7L);
        assertThat(summary.memberCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("membersPreview 에 color 포함 — preferredColor 설정한 사용자는 그 색, 안 한 사용자는 null")
    void list_membersPreviewIncludesColor() {
        Membership my30 = Membership.of(30L, 1L, MemberRole.ADMIN, null);
        given(membershipRepository.findByUserId(1L)).willReturn(List.of(my30));
        CareGroup g30 = CareGroup.create("색상 확인", 1L);
        setId(g30, 30L);
        given(careGroupRepository.findAllById(List.of(30L))).willReturn(List.of(g30));
        given(membershipRepository.findByCareGroupId(30L)).willReturn(List.of(
                Membership.of(30L, 1L, MemberRole.ADMIN, null),
                Membership.of(30L, 2L, MemberRole.PATIENT, 1L)));
        User colored = User.dummy("나");
        org.springframework.test.util.ReflectionTestUtils.setField(colored, "preferredColor", "#EC407A");
        given(userRepository.findById(1L)).willReturn(Optional.of(colored));
        given(userRepository.findById(2L)).willReturn(Optional.of(User.dummy("멤버2")));
        given(activityFeedRepository.findByActorSince(anyLong(), any(), anyInt())).willReturn(List.of());

        MyGroupSummary summary = sut.listMyGroups(1L).get(0);

        assertThat(summary.membersPreview())
                .extracting(MyGroupSummary.MemberPreview::userId, MyGroupSummary.MemberPreview::color)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "#EC407A"),
                        org.assertj.core.groups.Tuple.tuple(2L, null));
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
