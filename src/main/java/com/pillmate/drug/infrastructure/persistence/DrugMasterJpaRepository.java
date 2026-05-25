package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugMaster;
import org.springframework.data.jpa.repository.JpaRepository;

interface DrugMasterJpaRepository extends JpaRepository<DrugMaster, String> {
}
