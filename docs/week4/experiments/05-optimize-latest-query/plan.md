# 조회 성능 실험 계획서: 최신순 최적화

- 실험 ID: `05-optimize-latest-query`
- 상태: 적용·비교 완료 — [전후 결과](report-index.md) · [용량](storage.md).
- 작성일: 2026-10-14
- 실행 버전: `40a08b3` 기반 `commerce-api:week4-brand-first-20261014`
- 공통 절차·결과 형식: [프로토콜](../protocol.md)
- 장비 사양: [EC2](../environments/ec2-seoul.md)

## 실험 내용

`sort=latest&page=0&size=20`을 무필터·브랜드 1·브랜드 601로 조회한다. 각 조건 워밍업 3회 후 단건 3회를 순차 측정한다. 서버 처리·전체 응답·SQL 시간을 기록하고, 별도 `EXPLAIN ANALYZE`로 읽은 행 수와 정렬 비용을 확인한다.

## 변경 사항

먼저 현재 상태를 측정한다. 이후 인덱스 후보는 무필터 `(deleted_at, created_at DESC, id ASC)`, 브랜드 필터 `(brand_id, deleted_at, created_at DESC, id ASC)`다. 적용 전후 실행계획과 응답을 비교한다. 두 인덱스를 EC2에 추가하고 JPA 선언에도 반영했다.

최신순 동점은 ID 오름차순을 유지한다. 페이지네이션 방식은 가격순까지 개선한 뒤 결정한다.

## 적용 전 기록

상품 100만 개, 현재 정렬은 `created_at DESC, id ASC`. N+1 제거와 전체 건수 조회 제거는 이미 적용돼 있다. [좋아요순 개선](../04-optimize-likes-query/report-brand-first.md)을 참고하며 최신순 수치는 별도로 측정한다.
