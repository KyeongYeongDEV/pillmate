package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugInteractionJpaRepository extends JpaRepository<DrugInteraction, Long> {

    @Query("SELECT d FROM DrugInteraction d WHERE d.drugCodeA IN :codes AND d.drugCodeB IN :codes")
    List<DrugInteraction> findByKdCodes(@Param("codes") List<String> codes);
}
