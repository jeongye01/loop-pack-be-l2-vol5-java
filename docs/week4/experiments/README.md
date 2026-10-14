# Week4 조회 성능 실험

| SLO 항목 | 목표 |
| --- | --- |
| 대상 | 최신순 상품 목록 · 브랜드 필터 없음 · 첫 페이지 20개 |
| 부하 조건 | 1,700 RPS · 60초 · VU 3,000 · 3회 |
| 응답 시간 | 클라이언트 기준 p99 ≤ 200ms |
| 성공률 | 시작 요청의 100% 정상 응답 · 오류/timeout 0건 |
| 요청 누락 | 미시작·미완료 0건 |

- [이번 주 변경사항](../이번%20주%20변경사항.md): 인덱스·SQL·캐시·로깅·API 분리 요약
- [공통 프로토콜](protocol.md): 실행·측정·중단·회복·결과 기록 방식
- [커밋별 일반 EXPLAIN](historical-explain.md): 과거 쿼리·인덱스 구성의 별도 로컬 재현 출력
- [실험 계획서 템플릿](experiment-plan-template.md)
- [서울 EC2 장비 사양](environments/ec2-seoul.md) · [시스템 구성도](../system-architecture.md)
- [01. Baseline](01-baseline/plan.md): 현재 데이터 분포와 부하 한계 탐색
- [02. Baseline 재측정](02-baseline/plan.md): 100만 상품 편중 데이터로 같은 실험 반복
- [03. 좋아요순 Baseline](03-baseline-likes/plan.md): 같은 데이터·장비에서 좋아요순 부하 한계 탐색
- [05. 최신순 최적화](05-optimize-latest-query/plan.md): 단건 측정·실행계획·읽기 흐름 비교
- [06. 가격순 현재 상태](06-optimize-price-query/report.md): 단건 측정·실행계획·읽기 흐름
- [07. 좋아요순 부하 테스트](07-load-likes/plan.md): 최적화 후 무필터 첫 페이지의 부하 한계·병목 탐색
- [08. 최신순 부하 한계](08-load-latest/plan.md): 100 RPS 간격으로 정상 처리 한계 탐색
- [11. 최신순 Redis 캐시](11-cache-latest/report.md): 고정 데이터 조회 성능과 회차별 편차
- [12. 준비 부하 후 캐시 재측정](12-cache-latest-warmed/report.md): 안정화 확인 후 1,700 RPS 3회
- [13. 서울 EC2 부하기 비교](13-cache-ec2-load/report.md): 같은 리전 사설 경로에서 캐시 부하 3회
- [14. 캐시 없는 EC2 부하·촬영](14-no-cache-ec2-video/report.md): 예열 후 1,700 RPS 1회, 촬영 당시 자원 상태
- [15. 캐시 hit Tomcat 스레드](15-cache-tomcat-threads/report.md): 공통 스레드 지표 추가·1,700 RPS 1회
- [16. Tomcat 스레드 덤프](16-cache-thread-dump/report.md): 실험 로그 출력 락 대기 확인
- [17. 상세 로그 OFF 비교](17-cache-trace-off/report.md): 출력 경합 제거 시 평균 23ms·p99 237ms
- [18. 비동기 실험 로그](18-cache-async-log/report.md): AsyncAppender 구현·로그 전수 보존·1회 비교
- [19. 상세 기록 1% 샘플링](19-cache-sampled-log/report.md): 1,700 RPS·3,000 VU 3회, p99 51 / 73 / 30ms
- [예비 측정 보관본](preliminary/README.md): 기존 로컬·시드니 결과

## 공통 인프라

실험마다 스크립트·Compose·CSV를 복사하지 않는다. 실험 폴더에는 계획·설정·결과를 두고, 실행 당시 공통 파일의 해시와 Git 버전을 기록한다.

| 책임 | 공통 파일 | 상태 |
| --- | --- | --- |
| 앱·DB 실행 | `docker/app-compose.yml`, `docker/ec2/db-compose.yml` | 서울 배포 완료. 실험 시 `docker/ec2/experiment-compose.yml` 추가 |
| 데이터 생성·검증·적재 | `docs/week4/seed-data/scripts/` | 서울 적재·전체 필드 체크섬 일치 확인 |
| 데이터 분포 집계 | [profile_dataset.py](support/profile_dataset.py) | CSV 체크섬 확인 후 JSON 생성 |
| 부하 실행·환경/자원 수집 | [run.py](support/run.py) | 서울 설정 반영. 단일 요청·상태 수집 확인 |
| k6 요청·응답 비교 | [products.js](support/products.js) | 요청별 ID·시간·오류 분류. 로컬 통제 응답 4종 검증 완료 |
| 결과 집계 | [report.py](support/report.py) | Trace·요약 JSON 집계. Run 전체 연결은 진단 실행으로 확인 |
| 실행 절차·결과 형식 | [protocol.md](protocol.md) | 공통 기준 |

```text
experiments/
├── protocol.md
├── experiment-plan-template.md
├── environments/               # 서울·기존 시드니 장비 기록
├── dataset-profile.json        # 공통 데이터 분포
├── support/                    # 모든 실험이 공유하는 실행·수집·집계 코드
├── preliminary/                # 기존 예비 측정 원본·당시 도구와 검토 문서
└── 01-baseline/
    ├── plan.md
    ├── config.json
    ├── report.md               # 실행 후 작성
    └── results/{runId}/         # 실행 후 생성
```

예비 측정 원본의 수치·환경·해시는 그대로 보존한다. 당시 실행기는 보관용이며 정식 실험은 `support/`를 사용한다. 정식 실행 전 정상 응답·불일치·HTTP 오류·timeout·수집 실패·회복을 확인하고, 서울 배포에서 요청별 Tomcat·SQL Trace가 수집되는지 검증한다.
