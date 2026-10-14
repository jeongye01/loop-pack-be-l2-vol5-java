# 캐시 설계 적용 후 실행 계획

| 순서 | 실행할 일 | 확인할 결과 |
| --- | --- | --- |
| 1 | 로컬 계약·캐시·ArchUnit·정적 검사 | 전체 TTL 270~330초, revision 변경·롤백·삭제 비노출·본인 확인 |
| 2 | 배포 준비·설정 확인 | 프론트 분리 API 연동, revision DDL, catalog-cache 활성화, 기존 실험용 캐시 OFF, 키 버전·실제 TTL |
| 3 | 정상 hit 기준선 | 예열 후 1,700 RPS·VU 3,000·60초·3회. 성공률·p99, Redis hit/miss, 인증·revision 포함 실제 DB 유입 |
| 4 | 상품 수정·좋아요·재고 쓰기 혼합 | 그룹 버전 변경 후 새 응답, TTL 갱신과 즉시 반영 구분, miss 급증·DB 부하 |
| 5 | [20번 스탬피드](20-cache-stampede/plan.md) | 인기 키 만료 후 동시 요청의 중복 DB 조회·풀 대기·재적재·p99 |
| 6 | Bulkhead·Single Flight 비교 | 기준선으로 동시 허용량·대기·거절 정책 결정 후 적용 전후 비교 |
| 7 | Redis 메모리·LRU 실험 | maxmemory 용량 확정 후 allkeys-lru 적용, evicted_keys·miss·DB 유입·지연 |
| 8 | Redis 장애·복구 | timeout·fallback 제어, DB 보호·거절률, 복구 재적재·이전 revision 미사용 |

정상 상태 목표는 1,700 RPS에서 p99 ≤ 200ms·시작 요청 성공률 100%·미시작/미완료 0건이다. 장애 상태의 허용 거절·응답 정책은 별도 결정한다. 성공 응답 지연만 보고 목표 달성을 판단하지 않는다.

1번 로컬 검증 완료: 전체 테스트 419개(일반 415개 + 별도 JVM 비동기 로그 4개, API·OpenAPI·ArchUnit 포함), 실패·건너뜀 0개. Checkstyle·diff 검사 통과. 캐시 hit 시 TTL이 연장되지 않는 것도 확인했다. 배포와 부하 실행은 아직 하지 않는다. Bulkhead 한도·거절 정책, Redis maxmemory·timeout은 미정이므로 해당 단계 전에 확정한다. 공통 실행·수집 도구와 [프로토콜](protocol.md)을 재사용한다.
