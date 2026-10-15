"""Dedicated buyers for mixed purchase load; never overwrite existing balances."""
import argparse
import json
import shlex
import subprocess
from pathlib import Path


def sql(config, key, query):
    inner = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B ' + shlex.quote(config['database'])
    command = ['ssh', '-i', key, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=5',
               config['sshUser'] + '@' + config['dbHost'],
               'sudo docker exec -i ' + config['dbContainer'] + ' sh -c ' + shlex.quote(inner)]
    result = subprocess.run(command, input=query, text=True, capture_output=True, timeout=60, check=True)
    return [line.split('\t') for line in result.stdout.splitlines()]


def snapshot(config, key, fixture):
    lo, hi = min(fixture['buyers']), max(fixture['buyers'])
    return {
        'stock': sql(config, key, 'SELECT product_id,quantity FROM stock WHERE product_id BETWEEN 1 AND 20 ORDER BY product_id;'),
        'orders': sql(config, key, f'SELECT o.id,o.buyer_id,o.status,COALESCE(o.amount,0),i.product_id,i.quantity,i.unit_price FROM orders o JOIN order_items i ON o.id=i.order_id WHERE o.buyer_id BETWEEN {lo} AND {hi} ORDER BY o.id;'),
        'points': sql(config, key, f'SELECT user_id,balance FROM point WHERE user_id BETWEEN {lo} AND {hi} ORDER BY user_id;'),
        'dbCounters': sql(config, key, "SHOW GLOBAL STATUS WHERE Variable_name IN ('Questions','Com_select','Com_insert','Com_update','Com_commit','Com_rollback');"),
        'locks': sql(config, key, "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';"),
    }


def main():
    p=argparse.ArgumentParser(); p.add_argument('--config',type=Path,required=True); p.add_argument('--ssh-key',required=True)
    p.add_argument('action',choices=['prepare','snapshot']); p.add_argument('--output',type=Path)
    a=p.parse_args(); c=json.loads(a.config.read_text()); path=a.config.parent/'fixture.json'
    if a.action=='prepare':
        if path.exists(): raise RuntimeError('Fixture already exists; do not allocate twice')
        products=sql(c,a.ssh_key,'SELECT id,price FROM product WHERE id BETWEEN 1 AND 20 AND deleted_at IS NULL ORDER BY id;')
        assert len(products)==20
        purchase=c.get('purchase', {})
        rate=purchase.get('rps', 10); vus=purchase.get('vus', 100)
        count=max(rate, c.get('likes', {}).get('pairRps', 0)) * c.get('durationSeconds', 60) + 50
        values=','.join('(NOW(6),NOW(6))' for _ in range(count))
        rows=sql(c,a.ssh_key, f'START TRANSACTION; INSERT INTO users(created_at,updated_at) VALUES '+values+f'; SET @first=LAST_INSERT_ID(); INSERT INTO point(user_id,balance,version,created_at,updated_at) SELECT id,1000000000,0,NOW(6),NOW(6) FROM users WHERE id BETWEEN @first AND @first+{count-1}; SELECT @first; COMMIT;')
        first=int(rows[0][0]); fixture={'buyers':list(range(first,first+count)), 'initialBalance':1000000000,
            'products':[{'id':int(i),'price':int(price)} for i,price in products]}
        path.write_text(json.dumps(fixture,indent=2)+'\n')
        c['purchase']={'rps':rate,'vus':vus,'buyers':fixture['buyers'][10:],'products':fixture['products']}
        a.config.write_text(json.dumps(c,indent=2)+'\n')
        print(f'Prepared {len(fixture["buyers"])} new buyers: {first}..{first+count-1}')
    else:
        fixture=json.loads(path.read_text()); data=snapshot(c,a.ssh_key,fixture)
        a.output.write_text(json.dumps(data,indent=2)+'\n'); print(a.output)

if __name__=='__main__': main()
