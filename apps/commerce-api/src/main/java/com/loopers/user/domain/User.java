package com.loopers.user.domain;

import com.loopers.domain.BaseEntity;

public class User extends BaseEntity {

    private Point point = new Point(0);

    public Point getPoint() {
        return point;
    }

    public void charge(long amount) {
        this.point = point.charge(amount);
    }

    public void pay(long amount) {
        this.point = point.pay(amount);
    }
}
