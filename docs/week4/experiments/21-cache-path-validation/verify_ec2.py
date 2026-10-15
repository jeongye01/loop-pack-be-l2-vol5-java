#!/usr/bin/env python3
"""배포 환경 회귀 검사. 전용 fixture만 쓰며 실제 TTL 만료까지 기다린다.

실행: python3 verify_ec2.py --ssh-key KEY --app-host HOST --db-host HOST --output result.json
관리자 암호는 SSH로 메모리에만 읽는다. 기존 데이터 수정/Redis FLUSH는 하지 않는다.
catalog revision은 공통이므로 테스트 CUD도 기존 캐시를 논리적으로 무효화한다.
"""
import argparse
import base64
import json
import shlex
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def main(argv=None):
    if not __debug__:
        raise RuntimeError('검증 assert가 비활성화되는 python -O 실행은 지원하지 않습니다.')
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ('ssh-key', 'app-host', 'db-host', 'output'):
        parser.add_argument('--' + arg, required=True)
    parser.add_argument('--database', default='perf_skewed_1m_v1')
    args = parser.parse_args(argv)
    results = {'startedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), 'scenarios': [], 'fixtures': {}}
    brands, products, users = [], [], []

    def save():
        Path(args.output).write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')

    def ssh(host, command, stdin=None):
        return subprocess.check_output(['ssh', '-i', args.ssh_key, '-o', 'BatchMode=yes',
                                        'ubuntu@' + host, command], input=stdin, text=True, timeout=30).strip()

    def sql(query):
        command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B ' + shlex.quote(args.database)
        return ssh(args.db_host, 'sudo docker exec -i week4-db-mysql-1 sh -c ' + shlex.quote(command), query)

    def redis(*cmd):
        return ssh(args.db_host, 'sudo docker exec week4-db-redis-master-1 redis-cli --raw ' + shlex.join(cmd))

    # Never print or persist container environment / administrator credentials.
    env = json.loads(ssh(args.app_host, 'sudo docker inspect week4-commerce-api-1 --format "{{json .Config.Env}}"'))
    secret = dict(v.split('=', 1) for v in env)['ADMIN_PASSWORD']
    auth = 'Basic ' + base64.b64encode(('admin:' + secret).encode()).decode()
    del secret, env

    def api(path, method='GET', body=None, user=None, expected=None):
        headers = {'Content-Type': 'application/json'}
        if path.startswith('/api-admin/'):
            headers['Authorization'] = auth
        elif user is not None:
            headers['X-USER-ID'] = str(user)
        request = urllib.request.Request('http://' + args.app_host + ':8080' + path,
                                         data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, data = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, data = error.code, json.load(error)
        assert status in (expected if expected is not None else (200, 201)), (method, path, status)
        return data.get('data')

    def ok(name, **evidence):
        results['scenarios'].append({'scenario': name, 'passed': True, **evidence})
        save()
        print('PASS ' + name, flush=True)

    def revisions():
        return dict(line.split('\t') for line in sql('SELECT namespace,revision FROM read_cache_revision').splitlines())

    def scope(before, changed):
        after = revisions()
        delta = {key for key in before.keys() | after.keys() if before.get(key) != after.get(key)}
        assert delta == set(changed), (delta, changed)

    try:
        for _ in range(2):
            users.append(int(sql('INSERT INTO users(created_at,updated_at) VALUES(NOW(6),NOW(6)); SELECT LAST_INSERT_ID();')))
        tag = 'cache-canary-' + uuid.uuid4().hex[:10]
        for suffix in ('A', 'B'):
            brands.append(api('/api-admin/v1/brands', 'POST', {'name': tag + suffix})['id'])
        for brand, price in ((brands[0], 100), (brands[0], 150), (brands[1], 200)):
            products.append(api('/api-admin/v1/products', 'POST', {'brandId': brand, 'name': tag + str(price), 'price': price})['id'])
        results['fixtures'] = {'brands': brands, 'products': products, 'users': users}
        save()
        a, a2, b = products
        alice, bob = users
        ids = ','.join(map(str, sorted(products)))

        def get(path, user=alice, expected=None):
            return api(path, user=user, expected=expected)

        routes = {sort + str(brand): '/api/v1/products?sort=' + sort + '&page=0&size=20' +
                  ('' if brand is None else '&brandId=' + str(brand))
                  for sort in ('latest', 'price_asc', 'likes_desc') for brand in (None, *brands)}
        routes.update({f'detail-{p}': f'/api/v1/products/{p}' for p in products})
        routes.update({'brands': '/api/v1/brands?page=0&size=20', 'counts': '/api/v1/products/like-counts?ids=' + ids,
                       'stocks': '/api/v1/products/stocks?ids=' + ids, 'mine': f'/api/v1/users/{alice}/likes'})
        for path in routes.values():
            assert get(path) == get(path)
        api('/api-admin/v1/brands?page=0&size=20')
        get(f'/api/v1/users/{bob}/likes', bob)
        get(f'/api/v1/users/{alice}/likes', bob, (404,))
        api('/api/v1/brands', expected=(401,))
        ok('조회 경로 반복 응답 일치·본인 경계', requestConditions=len(routes) + 2)

        before = revisions()
        api(f'/api-admin/v1/products/{a}', 'PUT', {'name': tag + '-changed', 'price': 250})
        scope(before, ['catalog'])
        for sort in ('latest', 'price_asc', 'likes_desc'):
            items = get(routes[sort + str(brands[0])])['content']
            item = next(x for x in items if x['id'] == a)
            assert item['name'] == tag + '-changed' and item['price'] == 250
        assert [x['id'] for x in get(routes['price_asc' + str(brands[0])])['content']] == [a2, a]
        assert get(f'/api/v1/products/{b}')['price'] == 200
        assert get(f'/api/v1/products/{a}')['price'] == 250
        ok('상품 수정 → catalog만 변경·이름/가격/가격순 갱신·다른 상품 유지')

        before = revisions()
        api(f'/api-admin/v1/brands/{brands[0]}', 'PUT', {'name': tag + '-renamed'})
        scope(before, ['catalog'])
        assert get(f'/api/v1/products/{a}')['brand']['name'] == tag + '-renamed'
        assert next(x for x in get(routes['brands'])['content'] if x['id'] == brands[0])['name'] == tag + '-renamed'
        ok('브랜드 수정 → 소속 상품·고객 브랜드 목록 갱신')

        for quantity in (1, 0):
            get(routes['stocks'])
            before = revisions()
            api(f'/api-admin/v1/products/{a}/stock', 'PUT', {'quantity': quantity})
            scope(before, ['stock'])
            assert next(x for x in get(routes['stocks']) if x['productId'] == a)['soldOut'] == (quantity == 0)
        ok('재고 변경 → stock만 변경·품절 즉시 반영')

        # Real expiry: no DEL / EXPIRE shortening. Warm own unique batch and ranking keys.
        counts_before = get(routes['counts'])
        ranking_path = routes['likes_desc' + str(brands[0])]
        ranking_before = get(ranking_path)
        rev = revisions()['catalog']
        count_key = 'like-counts::v3:products/like-counts?ids=' + ids + ':rev=' + rev
        rank_key = f'rankings::v3:products?sort=likes_desc&brand={brands[0]}&page=0&size=20:rev=' + rev
        initial_ttl = {key: int(redis('TTL', key)) for key in (count_key, rank_key)}
        assert all(0 < ttl <= 330 for ttl in initial_ttl.values()), initial_ttl
        before = revisions()
        api(f'/api/v1/products/{a2}/likes', 'POST', user=alice)
        scope(before, [f'likes:{alice}'])
        assert [x['id'] for x in get(routes['mine'])['content']] == [a2]
        assert get(f'/api/v1/users/{bob}/likes', bob)['content'] == []
        assert get(routes['counts']) == counts_before
        assert get(ranking_path) == ranking_before
        after_ttl = {key: int(redis('TTL', key)) for key in initial_ttl}
        assert all(0 < after_ttl[k] <= initial_ttl[k] for k in initial_ttl)
        ok('좋아요 등록 → 본인 목록만 즉시 변경·숫자/순위 유지·hit TTL 비연장', initialTtl=initial_ttl, hitTtl=after_ttl)
        deadline = time.monotonic() + max(after_ttl.values()) + 2
        print('WAIT 실제 TTL 자연 만료 (최대 330초)', flush=True)
        while time.monotonic() < deadline:
            time.sleep(min(10, max(0, deadline - time.monotonic())))
        assert all(int(redis('EXISTS', k)) == 0 for k in initial_ttl)
        refreshed = get(routes['counts'])
        assert next(x for x in refreshed if x['productId'] == a2)['likeCount'] == 1
        assert get(ranking_path)['content'][0]['id'] == a2
        ttl = {k: int(redis('TTL', k)) for k in initial_ttl}
        assert all(268 <= x <= 330 for x in ttl.values()), ttl
        ok('TTL 자연 만료 → 키 소멸·좋아요 수/순위 갱신·270~330초 재적재', reloadedTtl=ttl)

        before = revisions()
        api(f'/api/v1/products/{a2}/likes', 'DELETE', user=alice)
        scope(before, [f'likes:{alice}'])
        assert get(routes['mine'])['content'] == []
        assert get(routes['counts']) == refreshed
        ok('좋아요 취소 → 본인 목록 즉시 제외·표시 숫자 TTL 유지')

        before = revisions()
        api(f'/api-admin/v1/products/{a}', 'DELETE')
        scope(before, ['catalog'])
        get(f'/api/v1/products/{a}', expected=(404,))
        for route in ('counts', 'stocks'):
            assert {x['productId'] for x in get(routes[route])} == {a2, b}
        assert [x['id'] for x in get(routes['latest' + str(brands[0])])['content']] == [a2]
        ok('상품 삭제 → 해당 상품만 상세/목록/일괄 조회에서 제외')

        before = revisions()
        api(f'/api-admin/v1/brands/{brands[0]}', 'DELETE')
        scope(before, ['catalog'])
        get(f'/api/v1/products/{a2}', expected=(404,))
        for route in ('counts', 'stocks'):
            assert [x['productId'] for x in get(routes[route])] == [b]
        assert all(x['id'] != brands[0] for x in get(routes['brands'])['content'])
        assert get(f'/api/v1/products/{b}')['price'] == 200
        ok('브랜드 삭제 → 소속 상품 제외·다른 브랜드 유지')
        results['passed'] = True
    except Exception as error:
        results['passed'] = False
        results['error'] = str(error)
        raise
    finally:
        cleanup_errors = []
        for brand in brands:
            try:
                api(f'/api-admin/v1/brands/{brand}', 'DELETE', expected=(200, 404))
            except Exception as error:
                cleanup_errors.append(str(error))
        if users:
            # No customer deletion API exists; tombstone only this run's users.
            try:
                sql('UPDATE users SET deleted_at=NOW(6),updated_at=NOW(6) WHERE id IN (' + ','.join(map(str, users)) + ');')
            except Exception as error:
                cleanup_errors.append(str(error))
        results['cleanup'] = {'mode': '전용 브랜드·상품·사용자 soft delete; 감사 기록 보존', 'errors': cleanup_errors}
        results['finishedAt'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        save()
        if cleanup_errors:
            raise RuntimeError('Fixture cleanup failed; inspect result file')
    return results


if __name__ == '__main__':
    main()
