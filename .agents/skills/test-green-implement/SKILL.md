---
name: test-green-implement
description: 작성된 Red 테스트를 절대 수정하지 않고 최소 production 코드만 작성해 전체 테스트를 Green으로 만들 때 사용한다.
context: fork
agent: general-purpose
---

# test-green-implement

분리된 컨텍스트에서 테스트와 현재 production 코드만 읽고 구현한다.

1. 시작할 때 모든 테스트 파일의 목록과 해시를 기록한다.
2. 실패하는 테스트를 실행한다.
3. 테스트를 통과시키는 최소 production 코드만 작성한다. 리팩터링이나 범위 밖 구현은 하지 않는다.
4. 관련 테스트와 전체 테스트를 실행해 모두 Green인지 확인한다.
5. 테스트 파일의 목록과 해시가 시작할 때와 같은지 확인한다.
6. Checkstyle과 `ArchitectureTest`를 실행한다.

테스트 코드, 기대값, 요구사항, 검사 규칙은 어떤 이유로도 수정·삭제·완화하지 않는다. 테스트와 구현이 충돌하면 테스트를 고치지 말고 중단해 보고한다.

다음 의존 방향을 지킨다.

```text
interfaces ──▶ application ──▶ domain ◀── infrastructure
```

- `domain`은 `interfaces`, `application`, `infrastructure`에 의존하지 않는다.
- `application`은 `interfaces`, `infrastructure`에 의존하지 않는다.
- `interfaces`는 `infrastructure`에 의존하지 않는다.
- 외부 기술 구현은 `infrastructure`에 두고 `domain`의 포트를 구현한다.
- `domain`은 HTTP와 DB 기술을 알지 않는다.
