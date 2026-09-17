package com.loopers.product.domain;

public record Stock(int quantity) {

    public Stock decrease(int amount) {
        return this;
    }
}
