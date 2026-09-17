package com.loopers.user.application;

import com.loopers.user.domain.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class PointUseCase {

    public PointUseCase(UserRepository userRepository) {
    }

    public long charge(Long userId, long amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public long getBalance(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
