package com.pillmate.drug.domain.repository;

import com.pillmate.drug.domain.model.Drug;

import java.util.List;
import java.util.Optional;

public interface DrugRepository {

    List<Drug> searchByKeyword(String keyword, int limit);

    Optional<Drug> findById(Long id);

    Optional<Drug> findByKdCode(String kdCode);

    Drug save(Drug drug);
}
