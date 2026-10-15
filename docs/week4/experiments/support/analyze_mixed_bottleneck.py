"""Analyze saved mixed traces; join purchase timings by request ID, never subtract percentiles."""
import argparse
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path


def distribution(values):
    values = sorted(values)
    if not values:
        return None
    return {'count': len(values), 'mean': statistics.mean(values), 'p50': values[len(values)//2],
            'p99': values[min(len(values)-1, int(len(values)*.99))], 'max': values[-1]}


def analyze(root):
    folder = sorted((root/'results').glob('*-measure-*'))[-1]
    run = json.loads((folder/'result.json').read_text())['runId']
    traces = [json.loads(line.split('EXPERIMENT_JSON ', 1)[1]) for line in (folder/'server.log').read_text().splitlines() if 'EXPERIMENT_JSON ' in line]
    traces = [t for t in traces if t['runId'] == run]
    by_id = {t['requestId']: t for t in traces}
    clients = [json.loads(line.split('PURCHASE_JSON ', 1)[1]) for line in (folder/'client.log').read_text().splitlines() if 'PURCHASE_JSON ' in line]
    matches = []
    for event in clients:
        prefix = f"{run}-purchase-{event['index']}-"
        if prefix+'create' not in by_id or prefix+'confirm' not in by_id:
            continue
        server_sum = sum(by_id[prefix+step]['serverDurationMs'] for step in ['create', 'confirm'])
        matches.append({'index': event['index'], 'clientMs': event['latencyMs'], 'serverSumMs': server_sum,
                        'outsideServerMs': event['latencyMs']-server_sum})
    reads = [t for t in traces if t['path'] == '/api/v1/products']
    writes = [t for t in traces if t['path'].startswith('/api/v1/orders')]
    def revisions(t):
        return sum('select namespace, revision' in q['sql'] for q in t['queries'])
    def origin(t):
        return any(k in t['stages'] for k in ['ProductUseCase.findBasicPage', 'ProductUseCase.findStockStatuses', 'ProductUseCase.findLikeCounts'])
    grouped = {}
    for label, group in [('read', reads), ('write', writes)]:
        stages = defaultdict(list); sql = defaultdict(list)
        for t in group:
            for k,v in t['stages'].items(): stages[k].append(v['totalMs'])
            for q in t['queries']:
                name = 'revision.read' if 'select namespace, revision' in q['sql'] else ('revision.write' if 'insert into read_cache_revision' in q['sql'] else 'other')
                sql[name].append(q['durationMs'])
        grouped[label] = {'count':len(group), 'serverMs':distribution([t['serverDurationMs'] for t in group]),
                          'connectionWaitMs':distribution([t['connectionWaitMs'] for t in group]),
                          'sqlMs':{k:distribution(v) for k,v in sql.items()},
                          'stagesTotalMsWhenPresent':{k:distribution(v) for k,v in stages.items()}}
    evidence = {'runId':run, 'groups':grouped, 'readRevisionCountHistogram':dict(Counter(revisions(t) for t in reads)),
                'readRecheckedWithoutOrigin':sum(revisions(t)>1 and not origin(t) for t in reads),
                'matchedPurchaseCount':len(matches), 'purchaseClientCount':len(clients),
                'matchedPurchase':{k:distribution([m[k] for m in matches]) for k in ['clientMs','serverSumMs','outsideServerMs']},
                'purchasePairs':matches,
                'notes':['Percentiles use sorted sample index floor(n*p), not k6 interpolation.',
                         'Outside-server residual includes queue/network/client processing; not a direct Tomcat queue timer.',
                         'Read traces are sampled. Stage totals overlap and must not be summed.',
                         'Repeated revision queries without origin are consistent with follower reprobes; not direct follower instrumentation.']}
    (root/'bottleneck-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
    return evidence


if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('experiment',type=Path);args=parser.parse_args()
    for root in sorted(args.experiment.glob('run-*')):
        result=analyze(root)
        print(json.dumps({'run':root.name,'purchase':result['matchedPurchase'],
                          'rechecksWithoutOrigin':result['readRecheckedWithoutOrigin'],
                          'revisionReads':result['readRevisionCountHistogram'],
                          'sql':{k:v['sqlMs'] for k,v in result['groups'].items()}},ensure_ascii=False))
