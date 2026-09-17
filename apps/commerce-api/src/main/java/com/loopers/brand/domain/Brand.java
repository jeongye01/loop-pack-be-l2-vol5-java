package com.loopers.brand.domain;

import com.loopers.domain.BaseEntity;

public class Brand extends BaseEntity {

    private String name;

    public Brand(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isDeleted() {
        return false;
    }

    public void update(String name) {
    }
}
