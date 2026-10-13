-- 좋아요순 후보의 삭제 여부·브랜드를 상품 전체 행 대신 인덱스로 확인한다.
ALTER TABLE perf_skewed_1m_v1.product
    ADD INDEX idx_product_likes_filter (deleted_at, id, brand_id),
    ALGORITHM=INPLACE,
    LOCK=NONE;
