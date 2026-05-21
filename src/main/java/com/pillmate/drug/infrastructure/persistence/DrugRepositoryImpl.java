package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.Drug;
import com.pillmate.drug.domain.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DrugRepositoryImpl implements DrugRepository {

    private final DrugJpaRepository jpaRepository;

    @Override
    public List<Drug> searchByKeyword(String keyword, int limit) {
        return jpaRepository.searchByTsv(keyword, limit);
    }

    @Override
    public Optional<Drug> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Drug> findByKdCode(String kdCode) {
        return jpaRepository.findByKdCode(kdCode);
    }

    @Override
    public Drug save(Drug drug) {
        return jpaRepository.save(drug);
    }
}
