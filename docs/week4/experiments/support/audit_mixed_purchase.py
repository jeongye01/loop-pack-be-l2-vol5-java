"""Reconcile actual purchase responses with DB orders, points and stock deltas."""
import json
import sys
from collections import Counter
from pathlib import Path

root=Path(sys.argv[1])
before=json.loads((root/'before.json').read_text())
after=json.loads((root/'after.json').read_text())
fixture=json.loads((root/'fixture.json').read_text())
folder=sorted((root/'results').glob('*-measure-*'))[-1]
run=json.loads((folder/'result.json').read_text())
metrics=json.loads((folder/'summary.json').read_text())['metrics']
events=[json.loads(line.split('PURCHASE_JSON ',1)[1]) for line in (folder/'client.log').read_text().splitlines() if 'PURCHASE_JSON ' in line]
old_orders={int(row[0]) for row in before['orders']}
orders={int(row[0]):list(map(int,row)) for row in after['orders'] if int(row[0]) not in old_orders}
confirmed=[row for row in orders.values() if row[2]==1]
quantities=Counter(); amounts=Counter()
for oid,buyer,status,amount,product,quantity,price in confirmed:
    quantities[product]+=quantity; amounts[buyer]+=amount
stock_before={int(i):int(q) for i,q in before['stock']}
stock_after={int(i):int(q) for i,q in after['stock']}
points_before={int(i):int(q) for i,q in before['points']}
points_after={int(i):int(q) for i,q in after['points']}
checks={
    'eventCountMatchesCompleted':len(events)==metrics['purchase_completed']['values']['count'],
    'oneOrderPerPurchase':len(orders)==len(events) and len({e['orderId'] for e in events})==len(events),
    'successResponsesMatchConfirmedOrders':all(e['orderId'] in orders and orders[e['orderId']][1:]==[e['buyer'],1,e['expectedAmount'],e['productId'],1,e['expectedAmount']] for e in events if e['outcome']=='success'),
    'stockDecrementsMatchConfirmedQuantities':all(stock_before[i]-stock_after[i]==quantities[i] for i in stock_before),
    'pointDebitsMatchConfirmedAmounts':all(points_before[i]-points_after[i]==amounts[i] for i in points_before),
}
result={'runId':run['runId'],'passed':all(checks.values()),'checks':checks,
    'createdOrders':len(orders),'confirmedOrders':len(confirmed),'clientSuccessfulPurchases':sum(e['outcome']=='success' for e in events),
    'confirmedDespiteClientFailure':[e['orderId'] for e in events if e['outcome']!='success' and e['orderId'] in orders and orders[e['orderId']][2]==1],
    'stockDecrements':{i:stock_before[i]-stock_after[i] for i in stock_before},
    'totalPointDebit':sum(points_before[i]-points_after[i] for i in points_before),
    'rowLockStatusDelta':{k:int(v)-int(dict(before['locks']).get(k,0)) for k,v in after['locks']}}
(root/'purchase-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
