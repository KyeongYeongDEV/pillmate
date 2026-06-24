package com.pillmate.user.infrastructure.persistence;

import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndExternalId(UserProvider provider, String externalId);
}
