-- 실험 DB에만 적용. 기존 사용자·상품 유일 인덱스는 유지한다.
ALTER TABLE perf_skewed_1m_v1.product_like
    ADD INDEX idx_product_like_product_id (product_id),
    ALGORITHM=INPLACE,
    LOCK=NONE;
