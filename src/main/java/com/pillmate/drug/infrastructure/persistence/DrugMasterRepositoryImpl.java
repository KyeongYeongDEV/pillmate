package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugMaster;
import com.pillmate.drug.domain.repository.DrugMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DrugMasterRepositoryImpl implements DrugMasterRepository {

    private final DrugMasterJpaRepository jpaRepository;

    @Override
    public Optional<DrugMaster> findByItemSeq(String itemSeq) {
        return jpaRepository.findById(itemSeq);
    }

    @Override
    public DrugMaster save(DrugMaster master) {
        return jpaRepository.save(master);
    }
}
