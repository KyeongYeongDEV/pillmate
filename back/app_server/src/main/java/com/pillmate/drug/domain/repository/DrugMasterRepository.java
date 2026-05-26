package com.pillmate.drug.domain.repository;

import com.pillmate.drug.domain.model.DrugMaster;

import java.util.Optional;

public interface DrugMasterRepository {

    Optional<DrugMaster> findByItemSeq(String itemSeq);

    DrugMaster save(DrugMaster master);
}
