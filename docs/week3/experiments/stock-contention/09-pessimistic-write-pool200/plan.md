# 동시성 실험 계획서: 비관적 쓰기 잠금

- 실험 ID: 09
- 상태: 실행 전
- 작성일: 2026-10-05
- 실행 버전: 실행 시 기록
- 공통 절차·결과 형식: [프로토콜](../../protocol.md)
- 시스템 스펙·시나리오·부하: [재고 경쟁 실험](../README.md)

## 실험 내용

DB 기본 격리 수준과 비관적 쓰기 잠금을 적용해 동시성 테스트 통과 여부와 주문 부하에서의 처리량·응답시간·기술 오류를 확인한다.

## 변경 사항

애플리케이션과 동시성 테스트에 [config.json](config.json)을 `SPRING_APPLICATION_JSON`으로 주입해 DB 기본 격리 수준과 Hikari 풀 200개를 적용한다. SQL 출력도 공통 측정 조건에 맞춰 끈다.

```yaml
datasource:
  mysql-jpa:
    main:
      # transaction-isolation은 설정하지 않고 MySQL 기본값을 사용한다.
```

변경 변수는 비관적 쓰기 잠금이다. DB 기본 격리 수준(REPEATABLE-READ), Hikari 풀 200개, 재시도 0회를 고정한다. 실제 커넥션에서 격리 수준과 풀 설정을 확인한다.

## 비교 목적

기존 01~04는 Hikari 최대 40개에서 실행되어 커넥션 풀 획득 시간 초과가 관찰됐다. 이 실험은 풀을 충분히 확보한 뒤 DB 잠금 경합만 다시 측정한다.

실행 방법은 [support/README.md](support/README.md), 결과는 [report.md](report.md)에 기록한다.
