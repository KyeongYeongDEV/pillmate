package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.CareGroup;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareGroupJpaRepository extends JpaRepository<CareGroup, Long> {}
