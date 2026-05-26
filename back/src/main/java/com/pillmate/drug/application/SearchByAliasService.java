package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.DrugMasterCandidate;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.model.DrugMaster;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import com.pillmate.drug.domain.repository.DrugMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchByAliasService {

    private final DrugAliasRepository drugAliasRepository;
    private final DrugMasterRepository drugMasterRepository;

    public List<DrugMasterCandidate> searchByAlias(String alias) {
        List<DrugAlias> aliases = drugAliasRepository.findByAlias(alias);
        return aliases.stream()
                .map(a -> toCandidate(a, drugMasterRepository.findByItemSeq(a.getItemSeq())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<DrugMasterCandidate> toCandidate(DrugAlias alias, Optional<DrugMaster> masterOpt) {
        return masterOpt.map(m -> new DrugMasterCandidate(
                m.getItemSeq(),
                m.getProductName(),
                m.getIngredientName(),
                m.getDoseAmount(),
                m.getDoseUnit(),
                m.getForm(),
                m.getCompany(),
                m.getImageUrl(),
                m.getLegacyDrugId(),
                alias.getConfidence()
        ));
    }
}
