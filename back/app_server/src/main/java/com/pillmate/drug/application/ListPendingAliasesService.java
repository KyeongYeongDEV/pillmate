package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.PendingAliasItem;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPendingAliasesService implements ListPendingAliasesUseCase {

    private final DrugAliasRepository drugAliasRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<PendingAliasItem> listPending(Pageable pageable) {
        return drugAliasRepository
                .findPendingBySource(AliasSource.USER, pageable)
                .map(this::toItem);
    }

    private PendingAliasItem toItem(DrugAlias a) {
        return new PendingAliasItem(
                a.getId(),
                a.getAlias(),
                a.getAliasJamo(),
                a.getItemSeq(),
                a.getSource().name(),
                a.getConfidence(),
                a.isVerified());
    }
}
