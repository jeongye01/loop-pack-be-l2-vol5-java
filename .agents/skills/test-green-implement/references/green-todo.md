# Green TODO

Green 에이전트가 구현 전에 현재 Red 테스트를 작은 책임 단위로 나눠 기록한다.

각 항목에는 요구사항 ID, 변경할 production 파일, 실행할 테스트를 적는다. 관련 테스트가 실제로 Green이 된 뒤에만 `[x]`로 바꾼다.

## 구현

- [ ] 요구사항 ID — 책임 — production 파일 — 관련 테스트

## 최종 검증

- [ ] 관련 테스트 전체 Green
- [ ] 전체 테스트 Green
- [ ] 테스트 파일 목록과 해시가 시작 시점과 동일
- [ ] Checkstyle 통과
- [ ] ArchitectureTest 통과
- [ ] production diff 확인
