"""실제 배포 서버 캐시 회귀 테스트. 실행 방법은 같은 폴더 README 참고.

한 fixture의 변경 전후를 비교하므로 9단계를 하나의 순차 시나리오로 실행한다.
실패 시 즉시 중단하되 verify_ec2의 finally에서 fixture를 정리한다.
"""
import os
import unittest

from verify_ec2 import main


@unittest.skipUnless(os.environ.get('RUN_EC2_CACHE_TESTS') == '1',
                     'EC2 쓰기 테스트는 RUN_EC2_CACHE_TESTS=1로 명시적으로 실행')
class Ec2CacheContractTest(unittest.TestCase):
    def test_read_write_expiry_and_deletion_contract(self):
        """준비: 전용 브랜드 2개·상품 3개·사용자 2명을 생성하고 응답을 캐시한다.

        실행·확인:
        1. 조회 반복: 응답 일치, 다른 사용자 목록/미인증 접근 거부.
        2. 상품 수정: 이름·가격·가격순 갱신, 다른 상품 값 유지.
        3. 브랜드 수정: 소속 상품·브랜드 목록 이름 갱신.
        4. 재고 변경: stock revision만 변경, 품절 즉시 반영.
        5. 좋아요 등록: 본인 목록만 즉시 갱신, 숫자·순위·다른 사용자 유지.
        6. 자연 만료: 실제 키 소멸 후 숫자·순위 갱신, TTL 재설정.
        7. 좋아요 취소: 본인 목록 즉시 제외, 표시 숫자는 TTL 동안 유지.
        8. 상품 삭제: 해당 상품만 비노출, 같은 브랜드의 다른 상품 유지.
        9. 브랜드 삭제: 소속 상품 비노출, 다른 브랜드 상품 유지.

        상세 HTTP·revision·Redis 기대값은 verify_ec2와 공유한다.
        """
        settings = ('EC2_SSH_KEY', 'EC2_APP_HOST', 'EC2_DB_HOST', 'EC2_CACHE_RESULT')
        missing = [key for key in settings if not os.environ.get(key)]
        self.assertFalse(missing, '실행 설정 누락: ' + ', '.join(missing))
        result = main([
            '--ssh-key', os.environ['EC2_SSH_KEY'],
            '--app-host', os.environ['EC2_APP_HOST'],
            '--db-host', os.environ['EC2_DB_HOST'],
            '--database', os.environ.get('EC2_DATABASE', 'perf_skewed_1m_v1'),
            '--output', os.environ['EC2_CACHE_RESULT'],
        ])
        self.assertTrue(result['passed'])
        self.assertEqual(9, len(result['scenarios']))
        for scenario in result['scenarios']:
            with self.subTest(scenario=scenario['scenario']):
                self.assertTrue(scenario['passed'])
        self.assertEqual([], result['cleanup']['errors'])


if __name__ == '__main__':
    unittest.main()
