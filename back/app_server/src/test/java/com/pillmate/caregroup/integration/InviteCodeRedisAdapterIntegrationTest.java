package com.pillmate.caregroup.integration;

import com.pillmate.caregroup.application.IssueInviteCodeUseCase;
import com.pillmate.caregroup.application.JoinGroupUseCase;
import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

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
@DisplayName("Invite Code Redis 통합 — SETEX TTL + JoinGroup 시나리오")
class InviteCodeRedisAdapterIntegrationTest {

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

    @Autowired InviteCodeCachePort inviteCodeCachePort;
    @Autowired IssueInviteCodeUseCase issueInviteCodeUseCase;
    @Autowired JoinGroupUseCase joinGroupUseCase;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired UserRepository userRepository;
    @Autowired CareGroupRepository careGroupRepository;
    @Autowired MembershipRepository membershipRepository;

    @Test
    @DisplayName("put → findGroupId 라운드트립 + Redis TTL 적용")
    void put_then_findGroupId_roundTrip() {
        String code = "RC0001";
        inviteCodeCachePort.put(code, 99L, Duration.ofMinutes(5));

        Optional<Long> found = inviteCodeCachePort.findGroupId(code);

        assertThat(found).contains(99L);
        Long ttlSec = stringRedisTemplate.getExpire("invite_code:" + code);
        assertThat(ttlSec).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("발급(POST) → Redis SETEX 24h → 가입(POST) 성공")
    void issue_then_join_endToEnd() {
        User admin = userRepository.save(User.dummy("admin-invite"));
        User patient = userRepository.save(User.dummy("patient-invite"));
        CareGroup group = careGroupRepository.save(CareGroup.create("g-invite", admin.getId()));
        membershipRepository.save(Membership.of(group.getId(), admin.getId(), MemberRole.ADMIN, null));

        InviteCodeResponse issued = issueInviteCodeUseCase.issue(group.getId(), admin.getId());

        Long ttlSec = stringRedisTemplate.getExpire("invite_code:" + issued.code());
        assertThat(ttlSec).isBetween(86000L, 86400L);

        Long joinedGroupId = joinGroupUseCase.join(issued.code(), patient.getId(), MemberRole.PATIENT);

        assertThat(joinedGroupId).isEqualTo(group.getId());
        assertThat(membershipRepository.existsByCareGroupIdAndUserId(group.getId(), patient.getId())).isTrue();
    }

    @Test
    @DisplayName("Redis 에 없는 코드 → PILL_096 EXPIRED_OR_INVALID")
    void join_whenCodeNotInRedis_throws096() {
        User patient = userRepository.save(User.dummy("patient-miss"));

        assertThatThrownBy(() -> joinGroupUseCase.join("NOEXIST", patient.getId(), MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_EXPIRED_OR_INVALID);
    }
}
