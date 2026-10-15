# Redis 장애 복구 검증 결과

**Redis가 꺼진 동안 변경된 DB 데이터는 반영하고, 복구 후에도 변경 전 옛 캐시는 사용하지 않는다.**

`캐시 100원 → Redis 중단 → DB 가격 400원으로 수정 → Redis 복구 → 새 revision으로 400원 캐시`

| 확인한 내용 | 결과 |
| --- | --- |
| Redis 중단 중 가격 수정 후 조회 | DB로 우회해 새 가격 400원 반환 |
| Redis 중단 중 상품 삭제 후 조회 | 404 반환 |
| 재시작 후 옛 캐시가 남아 있는 경우 | 옛 키 무시, 새 revision 키에 변경된 데이터 저장 |
| 복구 후 반복 조회 | 캐시 hit, 원본 상품 조회 생략 |
| 복구 후 삭제 상품 조회 | 404 유지 |

2026-10-15, 로컬 실제 MySQL·Redis Testcontainers에서 검증. **관련 테스트 33개·Checkstyle 통과**, 실패·건너뜀 0개. EC2·장애 중 부하·느린 네트워크 검증은 포함하지 않았다.

[상세 실험 기록](experiments/22-cache-redis-recovery/report.md) · [테스트 코드](../../apps/commerce-api/src/test/java/com/loopers/config/cache/RedisRecoveryHttpTest.java)

후속으로 [사용자 경계·주문·브랜드 삭제 보호 조건](캐시%20변경·장애%20보호%20검증%20결과.md)도 추가 검증했다. 반복 장애에서 약 60초 대기가 관측되어, 위 단건 성공이 장애 응답 지연까지 보장하지는 않는다.

후속 설정 개선으로 명령·연결 timeout 각 100ms, 재연결 대기 최대 1초를 적용했다. **단독 복구 3회와 전체 회귀 내 복구 3회 모두 통과**, 전체 테스트 결과 452개 통과. [개선 후 결과](experiments/22-cache-redis-recovery/timeout-fix-full-check.json). EC2 배포·장애 부하 SLO 검증은 별도다.
