# 동시성 실험 계획서: SERIALIZABLE

- 실험 ID: 01
- 상태: 동시성 테스트 10회·S1 본 측정 9회 완료. S2·S3는 2종/3종 비율 결정 대기
- 작성일: 2026-10-05
- 실행 버전: `ac0496e` + 이 폴더의 미커밋 실험 설정·지원 코드
- 공통 절차·결과 형식: [프로토콜](../../protocol.md)
- 시스템 스펙·시나리오·부하: [재고 경쟁 실험](../README.md)

## 실험 내용

기본 격리 수준을 `SERIALIZABLE`로 바꿔 동시성 테스트 통과 여부와 주문 부하에서의 처리량·응답시간·기술 오류를 확인한다.

## 변경 사항

애플리케이션과 동시성 테스트에 [config.json](config.json)을 `SPRING_APPLICATION_JSON`으로 주입해 Hikari 기본 격리 수준을 변경한다. SQL 출력도 공통 측정 조건에 맞춰 끈다.

```yaml
datasource:
  mysql-jpa:
    main:
      transaction-isolation: TRANSACTION_SERIALIZABLE
```

변경 변수는 격리 수준 하나다. 별도 행 잠금·버전 검사·조건부 갱신·재시도는 추가하지 않는다. 실제 커넥션에서 `SELECT @@session.transaction_isolation`으로 적용을 확인한다.

## 적용 전 기록

[주문 동시성 테스트 결과](../../concurrency-test-results.md): 2026-10-04, 테스트 4개를 각각 10회 실행해 **총 40건 모두 실패**했다.

실행 방법은 [support/README.md](support/README.md), 결과는 [report.md](report.md)에 기록한다.
