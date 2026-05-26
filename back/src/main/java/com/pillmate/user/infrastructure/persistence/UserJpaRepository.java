package com.pillmate.user.infrastructure.persistence;

import com.pillmate.user.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, Long> {}
