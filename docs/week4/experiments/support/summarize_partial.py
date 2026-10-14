"""Supplement failed runs without changing result.json or relaxing full-collection validation.

Usage: python3 summarize_partial.py EXPERIMENT
Duplicate server request IDs are excluded entirely, not arbitrarily deduplicated.
"""
import json
import sys
from collections import Counter
from pathlib import Path
from report import aggregate, events

root = Path(sys.argv[1])
for run_id in json.loads((root / 'comparison-runs.json').read_text()):
    directory = root / 'results' / run_id
    original = json.loads((directory / 'result.json').read_text())
    if original.get('requests'):
        continue
    client = events(directory / 'client.log', 'TRACE_JSON ')
    server = [x for x in events(directory / 'server.log', 'EXPERIMENT_JSON ') if x['runId'] == run_id]
    counts = Counter(x['requestId'] for x in server)
    duplicates = {key: value for key, value in counts.items() if value > 1}
    unique = [x for x in server if counts[x['requestId']] == 1]
    traces, report = aggregate(original['config'], client, unique,
                               json.loads((directory / 'summary.json').read_text()))
    analysis = dict(original, **report)
    analysis['analysisNote'] = 'Partial supplement only; original failed result is unchanged. Duplicate server IDs excluded entirely.'
    analysis['excludedDuplicateServerIds'] = duplicates
    analysis['status'] = 'failed'
    analysis['validation']['measurementComplete'] = False
    (directory / 'analysis.json').write_text(json.dumps(analysis, ensure_ascii=False, indent=2) + '\n')
    with (directory / 'traces.jsonl').open('w') as stream:
        for trace in traces:
            stream.write(json.dumps(trace, ensure_ascii=False) + '\n')
    print(run_id, report['requests'], 'excluded duplicate IDs:', len(duplicates))
