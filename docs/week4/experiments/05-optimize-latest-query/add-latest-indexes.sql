-- Existing populated DB: JPA ddl-auto is disabled. Keep previous indexes.
ALTER TABLE perf_skewed_1m_v1.product
    ADD INDEX idx_product_latest (deleted_at, created_at DESC, id ASC),
    ADD INDEX idx_product_brand_latest (brand_id, deleted_at, created_at DESC, id ASC),
    ALGORITHM=INPLACE,
    LOCK=NONE;
