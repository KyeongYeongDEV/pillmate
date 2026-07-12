package com.pillmate.user.application;

import com.pillmate.caregroup.application.LeaveGroupUseCase;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WithdrawUserService {

    private final UserRepository userRepository;
    private final LeaveGroupUseCase leaveGroupUseCase;
    private final Clock clock;

    // 회원탈퇴 — soft delete(익명화) + 모든 그룹 soft 탈퇴. 하드 DELETE 없음(db-safety).
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_AUTH_TOKEN));
        leaveGroupUseCase.leaveAll(userId);
        user.withdraw(Instant.now(clock));
        userRepository.save(user);
    }
}
