package com.pillmate.caregroup.integration;

import com.pillmate.activity.application.ActivityFeedQueryService;
import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.GetGroupDetailService;
import com.pillmate.caregroup.application.PinGroupUseCase;
import com.pillmate.caregroup.application.UnpinGroupUseCase;
import com.pillmate.caregroup.application.dto.GroupDetailResponse;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test"
})
@Testcontainers
@Transactional
@DisplayName("CareGroup 통합 — Pin + GroupDetail + ActivityGroupFilter")
class CareGroupIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired PinGroupUseCase pinGroupUseCase;
    @Autowired UnpinGroupUseCase unpinGroupUseCase;
    @Autowired GetGroupDetailService getGroupDetailService;
    @Autowired ActivityFeedQueryService activityFeedQueryService;
    @Autowired UserRepository userRepository;
    @Autowired CareGroupRepository careGroupRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired ActivityFeedRepository activityFeedRepository;

    @BeforeEach void setUp() { UserContext.clear(); }
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("한 사용자 두 그룹 핀 시 이전 핀 자동 해제")
    void pin_secondGroup_unpinsFirst() {
        User user = userRepository.save(User.dummy("user-pin"));
        CareGroup g1 = careGroupRepository.save(CareGroup.create("g1", user.getId()));
        CareGroup g2 = careGroupRepository.save(CareGroup.create("g2", user.getId()));
        membershipRepository.save(Membership.of(g1.getId(), user.getId(), MemberRole.ADMIN, null));
        membershipRepository.save(Membership.of(g2.getId(), user.getId(), MemberRole.ADMIN, null));

        pinGroupUseCase.pin(g1.getId(), user.getId());
        pinGroupUseCase.pin(g2.getId(), user.getId());

        Membership pinned = membershipRepository.findPinnedByUserId(user.getId()).orElseThrow();
        assertThat(pinned.getCareGroupId()).isEqualTo(g2.getId());

        Membership oldPin = membershipRepository.findByCareGroupIdAndUserId(g1.getId(), user.getId()).orElseThrow();
        assertThat(oldPin.isPinned()).isFalse();
    }

    @Test
    @DisplayName("unpin 호출 후 핀 없음")
    void unpin_removesPinned() {
        User user = userRepository.save(User.dummy("user-unpin"));
        CareGroup g = careGroupRepository.save(CareGroup.create("g", user.getId()));
        membershipRepository.save(Membership.of(g.getId(), user.getId(), MemberRole.ADMIN, null));
        pinGroupUseCase.pin(g.getId(), user.getId());

        unpinGroupUseCase.unpin(g.getId(), user.getId());

        assertThat(membershipRepository.findPinnedByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("그룹 상세 — 비멤버 접근 시 GROUP_ACCESS_DENIED")
    void detail_nonMember_throws() {
        User owner   = userRepository.save(User.dummy("owner"));
        User outsider = userRepository.save(User.dummy("outsider"));
        CareGroup g = careGroupRepository.save(CareGroup.create("owner-group", owner.getId()));
        membershipRepository.save(Membership.of(g.getId(), owner.getId(), MemberRole.ADMIN, null));

        assertThatThrownBy(() -> getGroupDetailService.detail(g.getId(), outsider.getId()))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("그룹 상세 — 멤버 + 최근 활동 반환")
    void detail_returnsMembersAndActivities() {
        User viewer = userRepository.save(User.dummy("viewer"));
        User other  = userRepository.save(User.dummy("other"));
        CareGroup g = careGroupRepository.save(CareGroup.create("g", viewer.getId()));
        membershipRepository.save(Membership.of(g.getId(), viewer.getId(), MemberRole.ADMIN, null));
        membershipRepository.save(Membership.of(g.getId(), other.getId(), MemberRole.PATIENT, viewer.getId()));
        activityFeedRepository.save(ActivityFeed.create(other.getId(), ActivityType.DOSE_TAKEN,
                TimeOfDay.MORNING, "아침약", ActivitySeverity.INFO));

        GroupDetailResponse response = getGroupDetailService.detail(g.getId(), viewer.getId());

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.recentActivities()).hasSize(1);
    }

    @Test
    @DisplayName("ActivityFeed groupId 필터 — 해당 그룹 멤버 활동만 반환")
    void activity_filterByGroupId() {
        User viewer = userRepository.save(User.dummy("viewer"));
        User memberA = userRepository.save(User.dummy("a"));
        User memberB = userRepository.save(User.dummy("b"));
        CareGroup gA = careGroupRepository.save(CareGroup.create("ga", viewer.getId()));
        CareGroup gB = careGroupRepository.save(CareGroup.create("gb", viewer.getId()));
        membershipRepository.save(Membership.of(gA.getId(), viewer.getId(), MemberRole.ADMIN, null));
        membershipRepository.save(Membership.of(gA.getId(), memberA.getId(), MemberRole.PATIENT, viewer.getId()));
        membershipRepository.save(Membership.of(gB.getId(), viewer.getId(), MemberRole.ADMIN, null));
        membershipRepository.save(Membership.of(gB.getId(), memberB.getId(), MemberRole.PATIENT, viewer.getId()));

        activityFeedRepository.save(ActivityFeed.create(memberA.getId(), ActivityType.DOSE_TAKEN,
                TimeOfDay.MORNING, "A 아침약", ActivitySeverity.INFO));
        activityFeedRepository.save(ActivityFeed.create(memberB.getId(), ActivityType.DOSE_TAKEN,
                TimeOfDay.MORNING, "B 아침약", ActivitySeverity.INFO));

        List<ActivityFeedItem> onlyA = activityFeedQueryService.query(viewer.getId(), gA.getId(), 20);
        assertThat(onlyA).hasSize(1);
        assertThat(onlyA.get(0).summary()).isEqualTo("A 아침약");
    }
}
