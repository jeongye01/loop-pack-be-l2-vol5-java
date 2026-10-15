"""Compare every like write response with dedicated users' final DB relations."""
import argparse
import json
from pathlib import Path
from mixed_purchase_fixture import sql

p=argparse.ArgumentParser();p.add_argument('--config',type=Path,required=True);p.add_argument('--ssh-key',required=True)
a=p.parse_args();root=a.config.parent;c=json.loads(a.config.read_text())
folder=next((root/'results').glob('*-measure-*'));events=[]
for line in (folder/'client.log').read_text().splitlines():
    if 'LIKE_JSON ' in line: events.append(json.loads(line.split('LIKE_JSON ',1)[1]))
users=c['purchase']['buyers'];remaining=sql(c,a.ssh_key,f'SELECT user_id,product_id FROM product_like WHERE user_id BETWEEN {min(users)} AND {max(users)} ORDER BY user_id,product_id;')
pairs={}
for e in events: pairs.setdefault(e['index'],[]).append(e)
valid=all(len(v)==2 and [x['step'] for x in v]==['register','cancel']
    and all(x['outcome']=='success' for x in v) and v[0]['user']==v[1]['user']
    and v[0]['productId']==v[1]['productId'] for v in pairs.values())
expected=c['likes']['pairRps']*c['durationSeconds']
result={'events':len(events),'pairs':len(pairs),'plannedPairs':expected,'remainingRelations':remaining,
    'allPairsSuccessful':valid,'passed':len(pairs)>=expected and valid and not remaining,
    'note':'Fresh users had no prior relations; each 201 registration must be followed by successful cancellation. Does not validate displayed count freshness.'}
(root/'like-audit.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if not result['passed']: raise SystemExit(1)
