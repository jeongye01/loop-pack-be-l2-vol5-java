# Baseline 실행 결과

준비·진단과 본 측정을 구분한다. 시간 단위는 ms.

<!-- GENERATED START -->
| Run | 구분 | 목표 RPS | timeout(초) | 완료 | 성공 | 미시작 | 성공 RPS | 전체 latency p95(ms) | 서버 p95(ms) | 상태 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [20261012T213720-diagnostic-1p0-7d62bf](results/20261012T213720-diagnostic-1p0-7d62bf/result.json) | diagnostic | 1.0 | 5 | 3 | 3 | 0 | 1.10 | 871.00 | 816.21 | passed |
| [20261012T213848-warmup-1p0-1b1358](results/20261012T213848-warmup-1p0-1b1358/result.json) | warmup | 1.0 | 5 | 30 | 30 | 0 | 1.01 | 826.00 | 757.31 | passed |
| [20261012T213947-measure-1p0-7a739b](results/20261012T213947-measure-1p0-7a739b/result.json) | measure | 1.0 | 5 | 61 | 61 | 0 | 1.00 | 817.00 | 778.08 | passed |
| [20261012T214124-warmup-2p0-39cc6a](results/20261012T214124-warmup-2p0-39cc6a/result.json) | warmup | 2.0 | 5 | 60 | 60 | 0 | 1.94 | 2588.00 | 2576.21 | passed |
| [20261012T214248-measure-2p0-b872ac](results/20261012T214248-measure-2p0-b872ac/result.json) | measure | 2.0 | 5 | 121 | 55 | 0 | 0.85 | 5002.00 | 7904.30 | failed |
| [20261012T214446-warmup-1p5-19b33c](results/20261012T214446-warmup-1p5-19b33c/result.json) | warmup | 1.5 | 5 | 46 | 46 | 0 | 1.50 | 702.00 | 687.83 | passed |
| [20261012T214637-measure-1p5-8365e6](results/20261012T214637-measure-1p5-8365e6/result.json) | measure | 1.5 | 5 | 91 | 91 | 0 | 1.50 | 721.00 | 705.80 | passed |
| [20261012T214824-warmup-1p75-8899e9](results/20261012T214824-warmup-1p75-8899e9/result.json) | warmup | 1.75 | 5 | 53 | 53 | 0 | 1.74 | 1004.00 | 966.01 | passed |
| [20261012T214909-measure-1p75-a61beb](results/20261012T214909-measure-1p75-a61beb/result.json) | measure | 1.75 | 5 | 105 | 105 | 0 | 1.74 | 1003.00 | 953.67 | passed |
| [20261012T215256-warmup-2p0-68abb5](results/20261012T215256-warmup-2p0-68abb5/result.json) | warmup | 2.0 | 10 | 60 | 60 | 0 | 1.91 | 3091.00 | 3073.92 | passed |
| [20261012T215341-measure-2p0-b78264](results/20261012T215341-measure-2p0-b78264/result.json) | measure | 2.0 | 10 | 121 | 121 | 0 | 1.91 | 5548.00 | 5538.84 | passed |
<!-- GENERATED END -->

## 타임아웃 10초 비교

2 RPS에서 타임아웃만 바꿔 각각 준비 30초 뒤 본 측정 60초를 실행했다. 각 조건 1회 결과이며 반복 검증 전이다.

| 타임아웃 | 성공 / 전체 | timeout | 클라이언트 p95 | 서버 p95 |
| --- | ---: | ---: | ---: | ---: |
| 5초 | 55 / 121 | 66 | 5.002초 | 7.904초 |
| 10초 | 121 / 121 | 0 | 5.548초 | 5.539초 |

5초 조건의 클라이언트 시간은 타임아웃으로 잘린 값이므로 서버 응답 완료 시간으로 해석하지 않는다. 두 실행의 성능 차이를 타임아웃 변경 효과만으로 단정하지 않는다.

10초 조건은 모든 응답 내용과 121건의 서버 Trace 연결을 확인했고, 미시작 요청은 없었다. 종료 후 회복도 확인했다. 요청 시작 시각으로 나눈 처음·중간·마지막 20초의 평균 응답 시간은 각각 1.990·4.182·5.253초다(각 40건, 60초 경계의 1건 제외). 대기 시간이 늘어나는 양상이므로 **60초 실행 통과를 지속 가능한 2 RPS의 증거로 보지 않는다.**

참고로 5초 조건의 1.75 RPS 본 측정은 105건 모두 성공했고 p95는 1.003초였다. 아직 경계 양쪽의 총 3회 반복은 완료하지 않았다.

## 일반 EXPLAIN

[출력 원본](explain-traditional/d80d218-baseline.txt) · [실행 SQL](explain-traditional/d80d218-baseline.sql) · [당시 구성 재현 정보](explain-traditional/d80d218-baseline.json).

해당 커밋의 쿼리·인덱스를 별도 로컬 MySQL 8.0.46에서 재현해 추가 수집했다. 기존 EC2 측정 당시 출력과는 구분한다. `rows`는 추정치이며 실제 읽은 행 수·loops·시간은 기존 `EXPLAIN ANALYZE`를 참고한다. [재현 범위](../historical-explain.md).
