# 스레드 덤프 — 실험 로그 출력 락 경합 확인

**Tomcat 워커 대부분이 실험용 상세 로그 출력 락에 묶여 있었다.** 200개 중 186 → 180 → 175개가 동일한 Logback `OutputStreamAppender` 락을 기다렸다. Tomcat 포화의 중요한 원인이 우리가 추가한 동기식 실험 로그임을 확인했다.

## 덤프 증거

| 스냅샷 | Tomcat 8080 워커 | 실험 로그 출력 락 대기 | 비율 |
| --- | ---: | ---: | ---: |
| 부하 약 15초 | 200 | 186 | 93.0% |
| 약 30초 | 200 | 180 | 90.0% |
| 약 45초 | 200 | 175 | 87.5% |

세 번 모두 대기 락 객체는 `0x0000000086efccf0`의 `ReentrantLock$NonfairSync`였다. 실제 상태 표기는 `WAITING (parking)`이며 Java의 `BLOCKED` 상태와 구분한다.

```text
요청 처리 완료
  → ExperimentConfiguration.TraceValve.log (136행)
  → Logger.info("EXPERIMENT_JSON ...")
  → OutputStreamAppender.writeBytes
  → ReentrantLock.lock
  → 같은 출력 락을 기다리며 Tomcat 워커 점유
  → 새 요청을 처리할 워커 부족
```

대표 스택:

```text
java.lang.Thread.State: WAITING (parking)
  parking to wait for <0x0000000086efccf0> (ReentrantLock$NonfairSync)
  ReentrantLock.lock
  OutputStreamAppender.writeBytes(OutputStreamAppender.java:211)
  Logger.info(Logger.java:588)
  ExperimentConfiguration$TraceValve.log(ExperimentConfiguration.java:136)
  AccessLogAdapter.log
  ContainerBase.logAccess
  CoyoteAdapter.service
```

현재 `local,docker,experiment` 프로필은 동기 ConsoleAppender를 사용한다. 코드에서 `serverDurationMs`는 Tomcat이 AccessLog 콜백에 넘긴 시간으로 먼저 채우고, 그 뒤 `LOG.info`를 실행한다. 따라서 **이 콜백의 로그 출력 대기는 기록된 서버 처리 시간에 포함되지 않는다.** 앱 active 카운터도 pipeline을 벗어나면서 먼저 감소한다. 내부 처리 시간은 짧은데 Tomcat busy가 200개인 현상과 맞아떨어진다.

## 부하 결과

앱 재시작 없이 700 RPS 30초 예열 후, 캐시 버전에 서울 EC2에서 1,700 RPS·VU 3,000·60초 1회. 데이터 변경 없음.

| 항목 | 결과 |
| --- | ---: |
| 시작·완료·성공 요청 | 102,001건 |
| 성공률 / 미시작 / timeout | 100% / 0 / 0 |
| 클라이언트 평균 / p95 / p99 | 521 / 702 / 742ms |
| 서버 내부 평균 / p99 | 10.6 / 41.4ms |
| Tomcat busy | 첫 표본 0, 이후 12개 표본 모두 200 |
| 앱 CPU 평균 — 전체 2코어 | 69.3% |
| DB CPU 평균 — 전체 2코어 | 22.5% |
| DB 커넥션 대기 평균 | 0.018ms |

요청·서버 로그 연결·공통 지표 검증은 통과했으나 p99 SLO 미달이다. 목표 102,000보다 1건 많은 실제 요청 수는 실행 경계값 그대로 보존했다.

## 해석과 다음 작업

스레드 수를 먼저 늘리기보다 **실험 로그의 직렬 출력 경합을 제거·완화하는 것이 우선**이다. 로그 비동기화·출력 대상 분리·샘플링 등을 비교할 수 있지만 아직 변경하지 않았다. 로그가 누락되지 않도록 큐 포화·유실 여부와 기존 검증 기준도 함께 설계해야 한다.

이번 결과는 세 순간의 스냅샷이며 누적 대기 시간이나 전체 지연의 정확한 기여율은 아니다. SIGQUIT 덤프 출력 자체도 지연을 추가할 수 있다. 15번에서 덤프 없이 이미 워커 포화를 관측했고 이번에는 그 대기 위치를 확인했다. 출력 락 내부에서 파일/콘솔 I/O 중 무엇이 얼마나 느린지는 별도 측정이 필요하다. 기존 부하 결과는 **동기 상세 계측 로그가 켜진 구성의 성능**으로 해석해야 하며 서비스 로직만의 처리 한계로 단정하지 않는다.

## 원본

- [덤프 1](results/20261014T184736-measure-1700-ef7031/thread-dump-1.txt) · [덤프 2](results/20261014T184736-measure-1700-ef7031/thread-dump-2.txt) · [덤프 3](results/20261014T184736-measure-1700-ef7031/thread-dump-3.txt)
- [분류 집계](thread-dump-summary.json) · [신호 시각](results/20261014T184736-measure-1700-ef7031/thread-dump-signals.json)
- [요청 결과](results/20261014T184736-measure-1700-ef7031/result.json) · [자원·Tomcat](results/20261014T184736-measure-1700-ef7031/resource-summary.json)

부하 종료 및 서버 회복 확인 완료. 캐시 버전과 기존 로깅 설정은 유지했다.
