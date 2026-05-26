package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.VerifyAliasResponse;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("VerifyAlias — 검수 통과")
@ExtendWith(MockitoExtension.class)
class VerifyAliasUseCaseTest {

    @Mock DrugAliasRepository drugAliasRepository;
    @InjectMocks VerifyAliasService sut;

    @Test
    @DisplayName("alias 검수 통과 시 is_verified=true, confidence=100 로 변경")
    void verify_setsVerifiedAndConfidence() {
        // given
        DrugAlias alias = DrugAlias.create("동광나자티딘", null, "200500823", AliasSource.USER, 70);
        given(drugAliasRepository.findById(1L)).willReturn(Optional.of(alias));
        given(drugAliasRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        VerifyAliasResponse resp = sut.verify(1L);

        // then
        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.confidence()).isEqualTo(100);
    }

    @Test
    @DisplayName("존재하지 않는 alias id 이면 ALIAS_NOT_FOUND 예외")
    void verify_whenNotFound_throwsAliasNotFound() {
        // given
        given(drugAliasRepository.findById(999L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.verify(999L))
                .isInstanceOf(PillmateException.class)
                .extracting(e -> ((PillmateException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALIAS_NOT_FOUND);
    }
}
