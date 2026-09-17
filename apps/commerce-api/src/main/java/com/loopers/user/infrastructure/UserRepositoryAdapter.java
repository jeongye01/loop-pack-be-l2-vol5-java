package com.loopers.user.infrastructure;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepository {

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
    }

    @Override
    public User save(User user) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<User> findById(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
