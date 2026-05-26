package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.DrugSearchResult;
import com.pillmate.drug.domain.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchDrugUseCase {

    private static final int DEFAULT_LIMIT = 20;

    private final DrugRepository drugRepository;
    private final DrugImageUrlResolver drugImageUrlResolver;

    public List<DrugSearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            throw new PillmateException(ErrorCode.DRUG_SEARCH_EMPTY_QUERY);
        }
        return drugRepository.searchByKeyword(query.trim(), DEFAULT_LIMIT)
                .stream()
                .map(drug -> DrugSearchResult.from(drug, drugImageUrlResolver.resolve(drug)))
                .toList();
    }
}
