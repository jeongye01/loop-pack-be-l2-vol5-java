# 캐시 유효 범위 검증

- [결과](report.md)
- [EC2 재실행 테스트](verify_ec2.py): 관리자 쓰기 API → 고객 조회로 응답·revision·TTL을 검사한다. 시나리오별 성공과 fixture ID를 JSON에 저장한다.
- 로컬 HTTP 테스트는 `CacheInvalidationScopeHttpTest`(쓰기 범위), `ReadCacheHttpTest`(TTL·인증·오류), `ReadCacheTtlTest`(jitter 범위)를 사용한다.

## 테스트 러너로 실행

[test_ec2_cache.py](test_ec2_cache.py)에 실행한 9단계를 준비·실행·확인 주석과 함께 보존했다. 실제 HTTP·Redis·revision 검증 로직은 `verify_ec2.py`를 공유하며, 기존 결과 JSON을 읽어 성공 처리하지 않는다. 일반 테스트 탐색에서는 건너뛰고 아래처럼 명시적으로 실행한다. fixture 상태를 이어서 비교하므로 9단계를 하나의 순차 테스트로 실행한다.

```sh
RUN_EC2_CACHE_TESTS=1 \
EC2_SSH_KEY=/path/to/key.pem \
EC2_APP_HOST=APP_IP EC2_DB_HOST=DB_IP \
EC2_CACHE_RESULT=/tmp/ec2-cache-result.json \
python3 -m unittest discover \
  -s docs/week4/experiments/21-cache-path-validation \
  -p 'test_ec2_cache.py' -v
```

일반 로컬 회귀 테스트:

```sh
./gradlew :apps:commerce-api:test \
  --tests 'com.loopers.config.cache.*' \
  --tests 'com.loopers.support.cache.ReadCacheTtlTest'
```

## 스크립트로 직접 실행

```sh
python3 docs/week4/experiments/21-cache-path-validation/verify_ec2.py \
  --ssh-key /path/to/key.pem \
  --app-host APP_IP --db-host DB_IP \
  --output docs/week4/experiments/21-cache-path-validation/ec2-write-validation.json
```

현재 EC2의 컨테이너 이름과 `local` 프로필 관리자 인증에 맞춘 스크립트다. DB 기본값은 `perf_skewed_1m_v1`이며 `--database`로 변경할 수 있다. 비밀번호를 결과 파일에 저장하지 않는다.

전용 브랜드 2개·상품 3개·사용자 2명을 만든다. 사용자 생성 API가 없어 사용자 fixture만 SQL로 생성한다. 종료 시 전용 브랜드와 소속 상품을 API로 삭제하고 사용자를 soft delete한다. 삭제 기록은 남으며 기존 seed를 변경하거나 Redis를 전체 삭제하지 않는다. **공통 catalog revision은 변경되므로 실행 중 기존 목록 캐시도 논리적으로 무효화된다.** 다른 쓰기·부하 실험과 동시에 실행하지 않는다.

좋아요 등록 후 실제 TTL(최대 330초)이 만료될 때까지 기다린다. 강제 만료를 쓰지 않는다. 생성·롤백·늦은 재삽입·주문 확정의 세밀한 검증은 로컬 통합 테스트에서 수행하며 이 EC2 스크립트의 검증 범위와 구분한다.
