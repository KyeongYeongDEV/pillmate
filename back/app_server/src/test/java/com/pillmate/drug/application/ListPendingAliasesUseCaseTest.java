package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.PendingAliasItem;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("ListPendingAliases — 검수 대기 목록 조회")
@ExtendWith(MockitoExtension.class)
class ListPendingAliasesUseCaseTest {

    @Mock DrugAliasRepository drugAliasRepository;
    @InjectMocks ListPendingAliasesService sut;

    @Test
    @DisplayName("source=USER AND is_verified=false 인 항목만 반환")
    void listPending_filtersUserAndUnverified() {
        // given
        DrugAlias alias = DrugAlias.create("동광나자티딘", null, "200500823", AliasSource.USER, 70);
        Page<DrugAlias> page = new PageImpl<>(List.of(alias));
        Pageable pageable = PageRequest.of(0, 20);
        given(drugAliasRepository.findPendingBySource(AliasSource.USER, pageable)).willReturn(page);

        // when
        Page<PendingAliasItem> result = sut.listPending(pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).source()).isEqualTo("USER");
        assertThat(result.getContent().get(0).isVerified()).isFalse();
    }
}
