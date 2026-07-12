package com.pillmate.user.application;

import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterPushTokenService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RegisterPushTokenServiceTest {

    @Mock UserRepository userRepository;
    @Mock com.pillmate.caregroup.domain.repository.MembershipRepository membershipRepository;
    @Mock com.pillmate.notification.application.port.RecipientCachePort recipientCachePort;
    @InjectMocks RegisterPushTokenService sut;

    @Test
    @DisplayName("Expo token 등록 → User.expoPushToken + provider 갱신")
    void register_updatesUserToken() {
        User user = User.dummy("alice");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        sut.register(1L, "ExponentPushToken[abc]", PushProvider.EXPO);

        assertThat(user.getExpoPushToken()).isEqualTo("ExponentPushToken[abc]");
        assertThat(user.getPushProvider()).isEqualTo(PushProvider.EXPO);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("존재하지 않는 user_id → IllegalArgumentException")
    void register_whenUserMissing_throws() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.register(99L, "tk", PushProvider.EXPO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // T-BE-REDIS-RECIPIENT-CACHE — 토큰 갱신 시 유저 소속 그룹 recipients 캐시 evict
    @Test
    @DisplayName("토큰 등록 시 유저 소속 모든 그룹 recipients 캐시 evict")
    void register_evictsUserGroupRecipientCaches() {
        User user = User.dummy("alice");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(membershipRepository.findByUserId(1L)).willReturn(java.util.List.of(
                com.pillmate.caregroup.domain.model.Membership.of(
                        10L, 1L, com.pillmate.caregroup.domain.model.MemberRole.PATIENT, null),
                com.pillmate.caregroup.domain.model.Membership.of(
                        20L, 1L, com.pillmate.caregroup.domain.model.MemberRole.PATIENT, null)));

        sut.register(1L, "ExponentPushToken[abc]", PushProvider.EXPO);

        verify(recipientCachePort).evict(10L);
        verify(recipientCachePort).evict(20L);
    }

    // T-BE-PUSH-TOKEN-DEDUP — 같은 기기 토큰이 다른 유저에게 재등록되면 이전 소유자 토큰 해제
    @Test
    @DisplayName("user2 가 user1 의 토큰 T 등록 → user1.expoPushToken null, user2 == T (1기기 1유저)")
    void register_dedupsTokenFromPreviousOwner() {
        String token = "ExponentPushToken[shared-device]";
        User user1 = userWithToken(1L, "alice", token);
        User user2 = userWithId(2L, "bob");
        given(userRepository.findByExpoPushTokenAndIdNot(token, 2L)).willReturn(java.util.List.of(user1));
        given(userRepository.findById(2L)).willReturn(Optional.of(user2));

        sut.register(2L, token, PushProvider.EXPO);

        assertThat(user1.getExpoPushToken()).isNull();
        assertThat(user2.getExpoPushToken()).isEqualTo(token);
        verify(userRepository).save(user1);
        verify(userRepository).save(user2);
    }

    @Test
    @DisplayName("토큰 뺏긴 이전 소유자의 그룹 recipient 캐시도 evict (본인+이전소유자 양쪽)")
    void register_evictsDispossessedUserGroups() {
        String token = "ExponentPushToken[shared-device]";
        User user1 = userWithToken(1L, "alice", token);
        User user2 = userWithId(2L, "bob");
        given(userRepository.findByExpoPushTokenAndIdNot(token, 2L)).willReturn(java.util.List.of(user1));
        given(userRepository.findById(2L)).willReturn(Optional.of(user2));
        given(membershipRepository.findByUserId(2L)).willReturn(java.util.List.of(
                com.pillmate.caregroup.domain.model.Membership.of(
                        20L, 2L, com.pillmate.caregroup.domain.model.MemberRole.PATIENT, null)));
        given(membershipRepository.findByUserId(1L)).willReturn(java.util.List.of(
                com.pillmate.caregroup.domain.model.Membership.of(
                        10L, 1L, com.pillmate.caregroup.domain.model.MemberRole.PATIENT, null)));

        sut.register(2L, token, PushProvider.EXPO);

        verify(recipientCachePort).evict(20L);
        verify(recipientCachePort).evict(10L);
    }

    @Test
    @DisplayName("동일 유저 재등록 — 뺏길 다른 유저 없음(빈 목록) → 자기 토큰 정상 유지")
    void register_sameUserReRegister_keepsOwnToken() {
        String token = "ExponentPushToken[abc]";
        User user = User.dummy("alice");
        given(userRepository.findByExpoPushTokenAndIdNot(token, 1L)).willReturn(java.util.List.of());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        sut.register(1L, token, PushProvider.EXPO);

        assertThat(user.getExpoPushToken()).isEqualTo(token);
    }

    private User userWithId(Long id, String name) {
        User user = User.dummy(name);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User userWithToken(Long id, String name, String token) {
        User user = userWithId(id, name);
        user.registerPushToken(token, PushProvider.EXPO);
        return user;
    }
}
