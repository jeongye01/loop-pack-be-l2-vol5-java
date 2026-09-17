package com.loopers.user.domain;

public record Point(long balance) {

    public Point charge(long amount) {
        return this;
    }

    public Point pay(long amount) {
        return this;
    }
}
