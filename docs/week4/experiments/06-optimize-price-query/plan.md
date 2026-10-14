# 조회 성능 실험 계획서: 가격순 최적화

- 실험 ID: `06-optimize-price-query`
- 상태: 적용·비교 완료 — [전후 결과](report-index.md) · [용량](storage.md).
- 작성일: 2026-10-14
- 실행 버전: `commerce-api:week4-brand-first-20261014` + 최신순 인덱스 적용 DB
- 공통 절차·결과 형식: [프로토콜](../protocol.md)
- 장비 사양: [EC2](../environments/ec2-seoul.md)

## 실험 내용

`sort=price_asc&page=0&size=20`을 무필터·브랜드 1·601로 조회한다. 각각 워밍업 3회 후 단건 3회 순차 측정. 목록 SQL·서버 처리·전체 응답과 별도 `EXPLAIN ANALYZE`의 읽은 행 수를 기록한다.

## 변경 사항

후보 인덱스는 `(deleted_at, price ASC, id ASC)`, `(brand_id, deleted_at, price ASC, id ASC)`. JPA 선언과 인덱스 검증 테스트를 맞춘다. 삭제·브랜드·가격 동점의 ID 오름차순·페이지 경계를 유지한다. DDL 직전·직후 EC2 디스크와 인덱스 크기를 수집한다.

## 적용 전 기록

[최신순 인덱스 실험](../05-optimize-latest-query/report-index.md)과 같은 장비·100만 상품 데이터. 최신순·좋아요순 인덱스는 유지하고 가격순 기준선은 별도로 측정한다. 페이지네이션 방식은 이번 비교 후 검토한다.
