package com.pillmate.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserProvider provider;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User dummy(String name) {
        User user = new User();
        user.provider = UserProvider.DUMMY;
        user.name = name;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    public static User ofOAuth(String externalId, UserProvider provider, String name, String email) {
        User user = new User();
        user.externalId = externalId;
        user.provider = provider;
        user.name = name;
        user.email = email;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }
}
