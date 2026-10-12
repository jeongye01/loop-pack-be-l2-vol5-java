"""Join client/server events and aggregate a single Run; missing data stays missing."""
import json
import math
from collections import Counter
from pathlib import Path


def events(path, marker):
    result = []
    if not path.exists():
        return result
    for line in path.read_text().splitlines():
        if marker in line:
            result.append(json.loads(line.split(marker, 1)[1]))
    return result


def distribution(values):
    values = sorted(v for v in values if v is not None)
    if not values:
        return {'count': 0, 'min': None, 'max': None, 'mean': None,
                'p50': None, 'p95': None, 'p99': None}
    return {'count':len(values), 'min':values[0], 'max':values[-1],
            'mean':sum(values)/len(values),
            **{f'p{p}': values[math.ceil(len(values)*p/100)-1] for p in (50,95,99)}}


def aggregate(config, client, server, summary):
    run_id = config['runId']
    starts, ends, servers = {}, {}, {}
    for event in client:
        if event['runId'] != run_id:
            continue
        target = starts if event['event'] == 'start' else ends
        if event['requestId'] in target:
            raise ValueError('Duplicate client event')
        target[event['requestId']] = event
    for event in server:
        if event['runId'] != run_id:
            continue
        if event['requestId'] in servers:
            raise ValueError('Duplicate server event')
        servers[event['requestId']] = event
    if not set(ends) <= set(starts):
        raise ValueError('Client end without start')
    traces = []
    for request_id, start in starts.items():
        end = ends.get(request_id)
        trace = dict(end or {'runId':run_id, 'requestId':request_id, 'outcome':'incomplete',
                            'startedAtMs':start['at'], 'finishedAtMs':None, 'latencyMs':None})
        trace.pop('event', None)
        remote = servers.get(request_id)
        trace['server'] = remote
        trace['serverDurationMs'] = remote['serverDurationMs'] if remote else None
        trace['queries'] = remote['queries'] if remote else None
        trace['measurementStatus'] = ('complete' if remote and not remote['sqlTruncated']
                                      and not remote['async'] else 'missing_or_partial')
        traces.append(trace)
    counts = Counter(t['outcome'] for t in traces)
    elapsed = (max(t['finishedAtMs'] for t in ends.values()) -
               min(t['startedAtMs'] for t in ends.values())) / 1000 if ends else None
    # An incomplete request means the final end time is unknown: do not report throughput.
    if len(ends) != len(starts):
        elapsed = None
    metrics = summary.get('metrics', {}) if summary else {}
    dropped = metrics.get('dropped_iterations', {}).get('values', {}).get('count', 0) if summary else None
    completed = len(ends)
    return traces, {
        'requests': {'planned':config['rps']*config['durationSeconds'], 'started':len(starts),
                     'completed':completed, 'incomplete':len(starts)-completed,
                     'dropped':dropped, 'byOutcome':dict(counts)},
        'metrics': {'elapsedSeconds':elapsed,
                    'completedRps': completed/elapsed if elapsed and elapsed > 0 else None,
                    'successRps': counts['success']/elapsed if elapsed and elapsed > 0 else None,
                    'latencyMs':distribution([t['latencyMs'] for t in traces]),
                    'latencyByOutcome':{o:distribution([t['latencyMs'] for t in traces if t['outcome']==o])
                                        for o in counts},
                    'serverDurationMs':distribution([t['serverDurationMs'] for t in traces]),
                    'sqlCount':distribution([len(t['queries']) if t['queries'] is not None else None for t in traces]),
                    'sqlExecutionMs':distribution([sum(q['durationMs'] for q in t['queries'])
                                                   if t['queries'] is not None else None for t in traces])},
        'validation': {'serverMatched':sum(t['server'] is not None for t in traces),
                       'serverUnmatchedIds': sorted(set(servers)-set(starts)),
                       'measurementComplete':bool(traces) and all(t['measurementStatus']=='complete' for t in traces),
                       'summaryPresent':summary is not None,
                       'summaryCountMatches':metrics.get('http_reqs',{}).get('values',{}).get('count') == completed},
        'percentileMethod':'nearest-rank; warmup excluded by separate Run',
    }


def write_report(experiment):
    rows = []
    for path in sorted((experiment/'results').glob('*/result.json')):
        r = json.loads(path.read_text())
        c, q, m = r['config'], r.get('requests', {}), r.get('metrics', {})
        def number(value): return '—' if value is None else f'{value:.2f}'
        latency = m.get('latencyMs', {})
        rows.append(f"| [{r['runId']}](results/{r['runId']}/result.json) | {c['phase']} | {c['rps']} | "
                    f"{q.get('completed','—')} | {q.get('byOutcome',{}).get('success',0)} | {q.get('dropped','—')} | "
                    f"{number(m.get('successRps'))} | {number(latency.get('p95'))} | "
                    f"{number(m.get('serverDurationMs',{}).get('p95'))} | {r['status']} |")
    generated = '\n'.join(['<!-- GENERATED START -->',
        '| Run | 구분 | 목표 RPS | 완료 | 성공 | 미시작 | 성공 RPS | 전체 latency p95(ms) | 서버 p95(ms) | 상태 |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |', *rows,
        '<!-- GENERATED END -->'])
    path = experiment/'report.md'
    if path.exists() and '<!-- GENERATED START -->' in path.read_text():
        old = path.read_text()
        before = old.split('<!-- GENERATED START -->')[0]
        after = old.split('<!-- GENERATED END -->',1)[1]
        path.write_text(before + generated + after)
    else:
        path.write_text('# Baseline 실행 결과\n\n준비·진단과 본 측정을 구분한다. 시간 단위는 ms.\n\n' + generated + '\n')
