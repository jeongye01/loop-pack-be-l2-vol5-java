# 실험 준비 툴

| 도구 | 하는 일 |
| --- | --- |
| k6 | 상품 목록 API에 부하를 주고 응답·클라이언트 시간을 기록 |
| Tomcat 접근 로그 + SQL 계측 | 요청별 서버 처리 시간·SQL·각 실행 시간·DB 연결 획득 대기를 기록 |
| Micrometer | DB 연결 사용·대기, JVM 메모리 등 앱 전체 상태를 측정 |
| Actuator | 앱 지표를 관리 HTTP API로 제공. 수집기가 5초 간격으로 조회 |
| SSH + 수집·집계 스크립트 | 서버 자원·앱 지표·로그를 로컬 파일로 모으고 Run별 결과를 집계 |

**Trace의 요청별 서버 시간은 Tomcat 로그를 사용한다.** Actuator·Micrometer는 같은 시각의 앱 전체 상태를 함께 보는 용도다. 요청별 기록은 `traces.jsonl`, 주기적인 상태 기록은 `resources.json`에 남긴다.

우선 k6 결과와 수집 파일로 실험한다. Prometheus 수집 서버·Grafana는 필요해지면 추가한다. 각 도구의 연결은 [시스템 구성도](system-architecture.md)를 참고한다.
