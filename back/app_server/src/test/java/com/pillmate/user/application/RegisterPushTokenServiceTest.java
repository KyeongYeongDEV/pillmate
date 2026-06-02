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
}
