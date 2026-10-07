# 동시성 실험 계획서: SERIALIZABLE

- 실험 ID: 05
- 상태: 실행 전
- 작성일: 2026-10-05
- 실행 버전: 실행 시 기록
- 공통 절차·결과 형식: [프로토콜](../../protocol.md)
- 시스템 스펙·시나리오·부하: [재고 경쟁 실험](../README.md)

## 실험 내용

기본 격리 수준을 `SERIALIZABLE`로 바꿔 동시성 테스트 통과 여부와 주문 부하에서의 처리량·응답시간·기술 오류를 확인한다.

## 변경 사항

애플리케이션과 동시성 테스트에 [config.json](config.json)을 `SPRING_APPLICATION_JSON`으로 주입해 SERIALIZABLE과 Hikari 풀 200개를 적용한다. SQL 출력도 공통 측정 조건에 맞춰 끈다.

```yaml
datasource:
  mysql-jpa:
    main:
      transaction-isolation: TRANSACTION_SERIALIZABLE
```

변경 변수는 실험 스펙(커넥션 풀)과 격리 수준이다. 별도 행 잠금·버전 검사·조건부 갱신·재시도는 추가하지 않는다. 실제 커넥션에서 격리 수준과 풀 설정을 확인한다.

## 비교 목적

기존 01~04는 Hikari 최대 40개에서 실행되어 커넥션 풀 획득 시간 초과가 관찰됐다. 이 실험은 풀을 충분히 확보한 뒤 DB 잠금 경합만 다시 측정한다.

실행 방법은 [support/README.md](support/README.md), 결과는 [report.md](report.md)에 기록한다.
