# Week4 조회 성능 실험

- [공통 프로토콜](protocol.md): 실행·측정·중단·회복·결과 기록 방식
- [실험 계획서 템플릿](experiment-plan-template.md)
- [서울 EC2 장비 사양](environments/ec2-seoul.md) · [시스템 구성도](../system-architecture.md)
- [01. Baseline](01-baseline/plan.md): 현재 데이터 분포와 부하 한계 탐색
- [02. Baseline 재측정](02-baseline/plan.md): 100만 상품 편중 데이터로 같은 실험 반복
- [03. 좋아요순 Baseline](03-baseline-likes/plan.md): 같은 데이터·장비에서 좋아요순 부하 한계 탐색
- [05. 최신순 최적화](05-optimize-latest-query/plan.md): 단건 측정·실행계획·읽기 흐름 비교
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
