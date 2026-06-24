package com.pillmate.user.domain.repository;

import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByProviderAndExternalId(UserProvider provider, String externalId);
    User save(User user);
}
