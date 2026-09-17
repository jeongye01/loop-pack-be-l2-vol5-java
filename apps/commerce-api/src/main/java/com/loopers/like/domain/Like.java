package com.loopers.like.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;

public class Like extends BaseEntity {

    private Long userId;
    private Long productId;

    public Like(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void cancel(Long requesterId) {
        if (!userId.equals(requesterId)) {
            throw new CoreException(ErrorCode.LIKE_NOT_FOUND);
        }
    }
}
