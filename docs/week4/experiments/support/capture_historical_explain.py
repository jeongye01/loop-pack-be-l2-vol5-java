"""Capture traditional EXPLAIN on an already loaded, isolated local replay DB.

Reads historical source with git show; never checks out or modifies application code.
Only adds historical indexes to perf_explain_history_v1, in chronological order.
The original seed DB and EC2 are never altered. Requires MYSQL_PWD and local MySQL.
"""
import csv
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[4]
EXP = ROOT / 'docs/week4/experiments'
JAVA = 'apps/commerce-api/src/main/java/com/loopers/'
DB = 'perf_explain_history_v1'
PRODUCT_COLUMNS = 'p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at'


def git_source(commit, path):
    return subprocess.check_output(['git', 'show', f'{commit}:{path}'], cwd=ROOT, text=True)


def mysql(sql, database=DB, table=False):
    args = ['mysql', '--protocol=TCP', '-h', '127.0.0.1', '-P', '13307', '-u', 'root',
            '--default-character-set=utf8mb4', '-D', database]
    args += ['--table', '--verbose'] if table else ['--batch', '--raw', '--skip-column-names']
    result = subprocess.run(args, input=sql, text=True, capture_output=True, check=True)
    if result.stderr.strip():
        raise RuntimeError(result.stderr)
    return result.stdout


def native_queries(source):
    result = {}
    for annotation, name in re.findall(r'@Query\((.*?)\)\s*(?:List<[^>]+>|long)\s+(\w+)\(', source, re.S):
        if 'nativeQuery = true' in annotation:
            result[name] = re.search(r'"""(.*?)"""', annotation, re.S)[1].strip()
    return result


def capture(folder, label, commit, mode, dataset, limit, additions=()):
    directory = EXP / folder / 'explain-traditional'
    directory.mkdir(exist_ok=True)
    database = 'perf_uniform_10k_v1' if dataset == 'uniform-10k-v1' else DB
    for table, name, columns in additions:
        present = mysql(f"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}' AND index_name='{name}';")
        if int(present.strip()) != 0:
            raise RuntimeError(f'Fresh replay DB required: index already exists: {name}')
        mysql(f'ALTER TABLE {table} ADD INDEX {name} ({columns});')
    mysql('ANALYZE TABLE product, product_like, stock, brand, users;', database)
    source = git_source(commit, JAVA + 'product/infrastructure/ProductJpaRepository.java')
    native = native_queries(source)
    cases = []
    brands = [7] if folder.startswith(('01-', '02-')) else [None, 1, 601]
    if folder.startswith('03-'):
        brands = [None, 7]
    for brand in brands:
        value = 'NULL' if brand is None else str(brand)
        condition = f'p.deleted_at IS NULL AND ({value} IS NULL OR p.brand_id={value})'
        if mode in ('price', 'latest'):
            field = 'p.price ASC' if mode == 'price' else 'p.created_at DESC'
            cases.append((f'{mode}; brand={value}; fetch={limit}; offset=0',
                          f'SELECT {PRODUCT_COLUMNS} FROM product p WHERE {condition} ORDER BY {field},p.id ASC LIMIT {limit} OFFSET 0'))
        elif not native:
            cases.append((f'likes join; brand={value}; fetch={limit}',
                          f'SELECT {PRODUCT_COLUMNS} FROM product p LEFT JOIN product_like l ON l.product_id=p.id WHERE {condition} GROUP BY p.id ORDER BY COUNT(l.id) DESC,p.id ASC LIMIT {limit} OFFSET 0'))
        else:
            for method, query in native.items():
                if method == 'findRankedCandidates' and brand is not None:
                    continue
                if method == 'findLikedCustomerProducts' and 'findRankedCandidates' in native and brand is None:
                    continue
                if method == 'countLikedCustomerProducts' and 'candidate.brand_id = :brandId' in native.get('findLikedCustomerProducts', '') and brand is None:
                    continue
                # Unliked/count queries are fallback paths, not first-page calls.
                bound = query.replace(':brandId', value).replace(':limit', str(limit)).replace(':offset', '0')
                if method in ('findCustomerProductsByLikes', 'findLikedCustomerProducts') and ':limit' not in query:
                    bound += f' LIMIT {limit} OFFSET 0'
                cases.append((f'{method}; brand={value}; fetch={limit}; offset=0', bound))
    if 'countCustomerProducts' in source:
        for brand in brands:
            value = 'NULL' if brand is None else str(brand)
            cases.append((f'total count; brand={value}', f'SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND ({value} IS NULL OR p.brand_id={value})'))
    # Representative per-product / batched auxiliary reads; EXPLAIN does not show repetition count.
    cases += [('single like count; representative product 980807', 'SELECT COUNT(id) FROM product_like WHERE product_id=980807'),
              ('single stock; representative product 980807', 'SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id=980807')]
    if commit in ('d2ab232','40a08b3','244d2e8','3bc0f0c'):
        ids = ','.join(str(i) for i in TOP20)
        cases += [('selected product detail; representative likes top20', f'SELECT {PRODUCT_COLUMNS} FROM product p WHERE p.id IN ({ids})'),
                  ('batch stock; representative likes top20', f'SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id IN ({ids})'),
                  ('batch likes; latest/price path; representative IDs', f'SELECT product_id,COUNT(id) FROM product_like WHERE product_id IN ({ids}) GROUP BY product_id')]
    if dataset == 'uniform-10k-v1':
        cases = [(name.replace('980807', '1'), query.replace('=980807', '=1')) for name, query in cases]
    sql = '\n\n'.join(f"SELECT '{name}' AS query_case;\nEXPLAIN FORMAT=TRADITIONAL {query};" for name, query in cases) + '\n'
    prefix = directory / label
    prefix.with_suffix('.sql').write_text(sql)
    prefix.with_suffix('.txt').write_text(mysql(sql, database, table=True))
    schema_sql = 'SELECT VERSION(),@@version_compile_machine,@@innodb_buffer_pool_size,@@optimizer_switch; SHOW CREATE TABLE product; SHOW CREATE TABLE product_like; SHOW INDEX FROM product; SHOW INDEX FROM product_like; SELECT COUNT(*) FROM product; SELECT COUNT(*) FROM product_like;'
    prefix.with_suffix('.schema.txt').write_text(mysql(schema_sql, database, table=True))
    sources = {}
    for path in ('product/infrastructure/ProductJpaRepository.java','product/infrastructure/ProductRepositoryAdapter.java','product/domain/Product.java','like/domain/Like.java'):
        contents = git_source(commit, JAVA + path)
        sources[JAVA + path] = hashlib.sha256(contents.encode()).hexdigest()
    metadata = {'capturedAt':datetime.now(timezone.utc).isoformat(), 'commit':subprocess.check_output(['git','rev-parse',commit],text=True).strip(),
                'database':database,'dataset':dataset,'mode':mode,'fetchLimit':limit,'caseCount':len(cases),
                'sourceSha256':sources,'datasetManifestSha256':hashlib.sha256((ROOT/'docs/week4/seed-data'/dataset/'manifest.json').read_bytes()).hexdigest(),
                'method':'Local reconstruction, not original EC2 output. Native SQL extracted from commit; JPQL translated to physical columns with equivalent filter/order/projection. Representative auxiliary reads are not a full HTTP request trace.',
                'indexAdditions':[{'table':t,'name':n,'columns':c} for t,n,c in additions]}
    prefix.with_suffix('.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
    print(f'{folder}/{label}: {len(cases)} EXPLAIN outputs', flush=True)


if __name__ == '__main__':
    assert os.environ.get('MYSQL_PWD'), 'Set local MySQL password in MYSQL_PWD'
    assert mysql('SELECT COUNT(*) FROM product;').strip() == '1000000'
    assert mysql('SELECT COUNT(*) FROM product_like;').strip() == '690000'
    assert mysql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name LIKE 'idx_product%';").strip() == '0', 'Requires freshly loaded replay DB'
    counts = Counter()
    with (ROOT/'docs/week4/seed-data/skewed-1m-v1/likes.csv').open() as stream:
        for row in csv.DictReader(stream):
            counts[int(row['product_id'])] += 1
    TOP20 = sorted(counts, key=lambda i:(-counts[i],i))[:20]
    capture('01-baseline','d80d218-baseline','d80d218','price','uniform-10k-v1',20)
    capture('02-baseline','c8d6468-baseline','c8d6468','price','skewed-1m-v1',20)
    capture('03-baseline-likes','e801069-baseline','e801069','likes','skewed-1m-v1',20)
    stages = [('770c082',20,[('product_like','idx_product_like_product_id','product_id')]),
              ('a4a5ec8',20,[]),('3575b51',20,[]),
              ('ce77577',20,[('product','idx_product_likes_filter','deleted_at,id,brand_id')]),
              ('1eed148',20,[]),('01420df',21,[]),('d2ab232',21,[]),('40a08b3',21,[])]
    for commit,limit,additions in stages:
        capture('04-optimize-likes-query',commit,commit,'likes','skewed-1m-v1',limit,additions)
    capture('05-optimize-latest-query','before-40a08b3','40a08b3','latest','skewed-1m-v1',21)
    capture('05-optimize-latest-query','after-244d2e8','244d2e8','latest','skewed-1m-v1',21,
            [('product','idx_product_latest','deleted_at,created_at DESC,id ASC'),('product','idx_product_brand_latest','brand_id,deleted_at,created_at DESC,id ASC')])
    capture('06-optimize-price-query','before-244d2e8','244d2e8','price','skewed-1m-v1',21)
    capture('06-optimize-price-query','after-3bc0f0c','3bc0f0c','price','skewed-1m-v1',21,
            [('product','idx_product_price','deleted_at,price ASC,id ASC'),('product','idx_product_brand_price','brand_id,deleted_at,price ASC,id ASC')])
