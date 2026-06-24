package com.pillmate.user.infrastructure.persistence;

import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public Optional<User> findById(Long id) { return jpa.findById(id); }

    @Override
    public Optional<User> findByProviderAndExternalId(UserProvider provider, String externalId) {
        return jpa.findByProviderAndExternalId(provider, externalId);
    }

    @Override
    public User save(User user) { return jpa.save(user); }
}
