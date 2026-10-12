# EC2 실험 장비 사양

- 작성·확인일: 2026-10-13
- specId: `ec2-sydney-v1`
- 확인 근거: 두 호스트 SSH의 `uname`, `nproc`, `free -b`, `df -B1 /`, Docker 목록, 앱 Java 버전, MySQL SELECT. 배포 정보는 [EC2 배포 문서](../../../../docker/ec2/README.md) 참조.

| 항목 | 앱 EC2 | DB EC2 |
| --- | --- | --- |
| 인스턴스 | m7i-flex.large | m7i-flex.large |
| CPU·RAM | 2 vCPU·8 GiB, OS 인식 8,153,149,440 bytes | 동일 |
| OS·아키텍처 | Ubuntu 26.04 LTS·x86_64 | 동일 |
| 커널 | 7.0.0-1006-aws | 동일 |
| 리전·AZ | ap-southeast-2·ap-southeast-2a | 동일 |
| 퍼블릭 IP | 3.106.241.33 | 16.176.204.86 |
| 프라이빗 IP | 172.31.2.234 | 172.31.14.182 |
| 루트 볼륨 | 20 GiB, 파일시스템 19,594,608,640 bytes | 동일 |
| swap | 없음 | 없음 |
| 실행물 | Spring Boot, Temurin 21.0.12.1 | MySQL 8.0.46, Redis 7.0 master/replica |

| 설정 | 현재 값·근거 |
| --- | --- |
| 앱 프로필 | `local,docker`, `ddl-auto=none`, SQL 출력 off (배포 설정) |
| Hikari | max 40, min idle 30, connection timeout 3,000ms (기존 배포·예비 측정) |
| Tomcat | max threads 200 (기존 배포 설정) |
| MySQL DB | `perf_uniform_10k_v1` |
| MySQL buffer pool | 134,217,728 bytes = 128 MiB (실 DB 조회) |
| MySQL max connections | 151 (실 DB 조회) |
| MySQL 격리 수준 | REPEATABLE-READ (조회 세션의 기본값; 앱 TX 설정은 Run에서 별도 확인) |
| 캐시 | Redis는 실행 중이나 현재 상품 목록 조회에는 캐시 없음 |
| 부하 발생기 | 로컬 Mac의 k6. 실제 사양·버전·상태는 Run마다 수집 |

로컬 k6 → 앱은 퍼블릭 HTTP, 앱 → MySQL·Redis는 프라이빗 IP를 사용한다. 클라이언트 시간에는 인터넷 구간의 영향이 섞인다. 앱·DB만 EC2로 분리했으므로 로컬 부하 생성기의 자원 상태도 함께 기록한다.

이 문서는 공통 장비 기준이며 실행 당시 상태의 대체물이 아니다. 각 Run에서 이미지/JAR 해시·JVM 힙·컨테이너 제한·디스크 I/O·지표 수집 설정을 확인한다. EBS 종류·IOPS/처리량은 AWS 설정 근거를 확보하기 전 미확인으로 둔다. 초기 사양은 baseline 중에 조정하지 않는다.
