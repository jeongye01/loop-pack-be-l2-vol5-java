# 서울 EC2 실험 장비 사양

- 작성·확인일: 2026-10-13
- specId: `ec2-seoul-v1`
- 확인 근거: 각 서버 SSH 접속 후 IMDS의 인스턴스 식별 문서와 `uname -m` 조회.
- 상태: 서비스 배포·데이터 적재·기준 응답·서버/SQL Trace·앱 지표 수집 확인 완료.

| 항목 | 앱 EC2 | DB EC2 |
| --- | --- | --- |
| 인스턴스 ID | i-03a1252457ad770f0 | i-039cd49b7ebda7ad6 |
| 인스턴스 유형 | m7i-flex.large | m7i-flex.large |
| 아키텍처 | x86_64 | x86_64 |
| 리전·AZ | ap-northeast-2 · ap-northeast-2b | 동일 |
| 퍼블릭 IP | 54.117.65.124 | 16.184.11.214 |
| 프라이빗 IP | 172.31.26.165 | 172.31.26.139 |
| 역할 | Spring Boot | MySQL·Redis master/replica |

| 실제 실행 설정 | 값 |
| --- | --- |
| CPU·메모리·OS | 양쪽 2 vCPU·약 8 GiB, Ubuntu 26.04 LTS, swap 없음 |
| 루트 파일시스템 | 양쪽 19,594,608,640 bytes. DB도 현재 20 GiB 볼륨이며 처음 계획한 30 GiB와 다름 |
| Docker / Java | Docker 29.9.0, Temurin 21.0.12.1 |
| 앱 프로필 | local,docker,experiment · ddl-auto=none |
| 앱 JVM 최대 힙 | 2,038,431,744 bytes (기본 자동 산정) |
| DB | MySQL 8.0.46, buffer pool 128 MiB, max connections 151, 기본 REPEATABLE-READ |
| Redis | 7.0 master/replica, master_link_status=up. 상품 목록 캐시 없음 |

확인 근거: [배포 직후 스냅샷](ec2-seoul-snapshot.json). 상품 10,000·좋아요 999,493개를 포함한 모든 CSV 필드가 적재 후 체크섬과 일치했다. 실험 중 사양은 바꾸지 않는다.

## 네트워크 사전 확인

로컬에서 각 서버의 TCP 22번 포트에 10회 연결했다. DNS·SSH 인증·API 처리는 제외하고 TCP 연결 수립 시간을 RTT 근사값으로 사용했다.

| 대상 | 성공 | 평균 | 중앙값 | 최소~최대 |
| --- | --- | --- | --- | --- |
| 서울 앱 | 10/10 | 6.21ms | 5.95ms | 5.18~8.42ms |
| 서울 DB | 10/10 | 6.30ms | 6.01ms | 5.11~8.41ms |

부하 경로는 로컬 k6 → 앱 퍼블릭 HTTP → DB 프라이빗 IP다. 위 RTT는 로컬에서 각각 측정한 값으로, 앱→DB RTT나 상품 조회 응답 시간이 아니다.

시드니 장비 기록은 [기존 사양](ec2.md)에 보존한다. 각 Run에서는 서울의 실제 실행 상태와 로컬 생성기 상태를 별도로 수집한다.
