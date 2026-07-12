package com.pillmate.user.domain.repository;

import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    List<User> findAllByIdIn(List<Long> ids);
    List<User> findByExpoPushTokenAndIdNot(String expoPushToken, Long keepUserId);
    Optional<User> findByProviderAndExternalId(UserProvider provider, String externalId);
    User save(User user);
}
