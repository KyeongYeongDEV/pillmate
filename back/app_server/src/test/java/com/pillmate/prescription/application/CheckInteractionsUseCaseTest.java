package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.InteractionWarning;
import com.pillmate.prescription.application.port.DrugInteractionPort;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.domain.model.InteractionSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@DisplayName("CheckInteractionsUseCase — DDI 병용금기 검증")
@ExtendWith(MockitoExtension.class)
class CheckInteractionsUseCaseTest {

    @Mock DrugInteractionPort drugInteractionPort;
    @Mock DrugLookupPort drugLookupPort;
    @InjectMocks CheckInteractionsService sut;

    @Test
    @DisplayName("병용금기 쌍이 존재하면 InteractionWarning 목록 반환")
    void check_returnsWarnings_whenPairExists() {
        // given
        given(drugLookupPort.findByKdCode("200500823")).willReturn(
                Optional.of(new DrugLookupPort.DrugSummary(1L, "200500823", "이트라코나졸캡슐", null)));
        given(drugLookupPort.findByKdCode("200300001")).willReturn(
                Optional.of(new DrugLookupPort.DrugSummary(2L, "200300001", "심바스타틴정", null)));
        given(drugInteractionPort.findByKdCodes(List.of("200500823", "200300001")))
                .willReturn(List.of(new DrugInteractionPort.DrugInteractionRecord(
                        "200500823", "200300001", "CRITICAL",
                        "횡문근융해증 위험", "식품의약품안전처")));

        // when
        List<InteractionWarning> result = sut.check(List.of("200500823", "200300001"));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo(InteractionSeverity.CRITICAL);
        assertThat(result.get(0).nameA()).isEqualTo("이트라코나졸캡슐");
        assertThat(result.get(0).nameB()).isEqualTo("심바스타틴정");
    }

    @Test
    @DisplayName("kdCode 가 2개 미만이면 빈 목록 반환 (쌍 불가)")
    void check_returnsEmpty_whenLessThanTwoCodes() {
        // given / when
        List<InteractionWarning> result = sut.check(List.of("200500823"));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("병용금기 데이터가 없으면 빈 목록 반환")
    void check_returnsEmpty_whenNoInteractionFound() {
        // given
        given(drugInteractionPort.findByKdCodes(anyList())).willReturn(List.of());

        // when
        List<InteractionWarning> result = sut.check(List.of("200500823", "200300001"));

        // then
        assertThat(result).isEmpty();
    }
}
