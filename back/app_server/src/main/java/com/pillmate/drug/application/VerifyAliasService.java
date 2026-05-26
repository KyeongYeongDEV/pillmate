package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.VerifyAliasResponse;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyAliasService implements VerifyAliasUseCase {

    private final DrugAliasRepository drugAliasRepository;

    @Override
    @Transactional
    public VerifyAliasResponse verify(Long aliasId) {
        DrugAlias alias = drugAliasRepository.findById(aliasId)
                .orElseThrow(() -> new PillmateException(ErrorCode.ALIAS_NOT_FOUND));
        alias.verify();
        DrugAlias saved = drugAliasRepository.save(alias);
        return toResponse(saved);
    }

    private VerifyAliasResponse toResponse(DrugAlias a) {
        return new VerifyAliasResponse(
                a.getId(),
                a.getAlias(),
                a.getItemSeq(),
                a.isVerified(),
                a.getConfidence());
    }
}
