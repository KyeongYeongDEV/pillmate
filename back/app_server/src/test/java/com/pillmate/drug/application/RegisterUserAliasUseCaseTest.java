package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.RegisterUserAliasCommand;
import com.pillmate.drug.application.dto.RegisterUserAliasResponse;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.model.DrugMaster;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import com.pillmate.drug.domain.repository.DrugMasterRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@DisplayName("RegisterUserAlias — 사용자 alias 등록")
@ExtendWith(MockitoExtension.class)
class RegisterUserAliasUseCaseTest {

    @Mock DrugMasterRepository drugMasterRepository;
    @Mock DrugAliasRepository drugAliasRepository;
    @InjectMocks RegisterUserAliasService sut;

    @Test
    @DisplayName("item_seq 가 drug_master 에 없으면 ITEM_SEQ_NOT_FOUND 예외")
    void register_whenItemSeqNotFound_throwsNotFound() {
        // given
        RegisterUserAliasCommand cmd = new RegisterUserAliasCommand("동광나자티딘캡슐150mg", "999999999");
        given(drugMasterRepository.findByItemSeq("999999999")).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.register(cmd))
                .isInstanceOf(PillmateException.class)
                .extracting(e -> ((PillmateException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_SEQ_NOT_FOUND);
    }

    @Test
    @DisplayName("유효한 요청 시 alias 삽입 후 응답 반환")
    void register_whenValid_insertsAlias() {
        // given
        RegisterUserAliasCommand cmd = new RegisterUserAliasCommand("동광나자티딘캡슐150mg", "200500823");
        DrugMaster master = DrugMaster.create(
                "200500823", "동광나자티딘캡슐150mg", null, null, null, null, null, null, null, "mfds", null);
        given(drugMasterRepository.findByItemSeq("200500823")).willReturn(Optional.of(master));
        given(drugAliasRepository.findByAliasAndItemSeq(any(), eq("200500823"))).willReturn(Optional.empty());
        given(drugAliasRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        RegisterUserAliasResponse resp = sut.register(cmd);

        // then
        assertThat(resp.alias()).isEqualTo("동광나자티딘캡슐150mg");
        assertThat(resp.source()).isEqualTo("user");
        assertThat(resp.confidence()).isEqualTo(70);
        assertThat(resp.isVerified()).isFalse();
    }

    @Test
    @DisplayName("중복 alias+item_seq 이면 기존 항목 반환 (ON CONFLICT DO NOTHING)")
    void register_whenDuplicate_returnsExisting() {
        // given
        RegisterUserAliasCommand cmd = new RegisterUserAliasCommand("동광나자티딘캡슐150mg", "200500823");
        DrugMaster master = DrugMaster.create(
                "200500823", "동광나자티딘캡슐150mg", null, null, null, null, null, null, null, "mfds", null);
        DrugAlias existing = DrugAlias.create("동광나자티딘캡슐150mg", null, "200500823", AliasSource.USER, 70);
        given(drugMasterRepository.findByItemSeq("200500823")).willReturn(Optional.of(master));
        given(drugAliasRepository.findByAliasAndItemSeq(any(), eq("200500823"))).willReturn(Optional.of(existing));

        // when
        RegisterUserAliasResponse resp = sut.register(cmd);

        // then
        assertThat(resp.alias()).isEqualTo("동광나자티딘캡슐150mg");
        assertThat(resp.itemSeq()).isEqualTo("200500823");
    }
}
