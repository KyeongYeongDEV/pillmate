package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.PendingAliasItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListPendingAliasesUseCase {
    Page<PendingAliasItem> listPending(Pageable pageable);
}
