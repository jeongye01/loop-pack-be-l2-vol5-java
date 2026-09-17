package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;

public class Product extends BaseEntity {

    private Long brandId;
    private String name;
    private long price;
    private Stock stock;

    public Product(Long brandId, String name, long price) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public Stock getStock() {
        return stock;
    }

    public boolean isDeleted() {
        return false;
    }

    public void update(String name, long price, Long brandId) {
    }

    public void changeStock(int quantity) {
    }

    public void decreaseStock(int quantity) {
    }
}
