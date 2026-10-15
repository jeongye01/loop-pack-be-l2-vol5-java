"""Summarize per-run original-load gauges/counters and k6 one-second windows."""
import json
import re
import sys
from pathlib import Path


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)


def summarize(folder):
    run = json.loads((folder / 'slo-result.json').read_text())
    result = json.loads((folder / 'result.json').read_text())
    origin = {}
    stock_origin = {}
    for body in strings(result['recovery']):
        for line in body.splitlines():
            if line.startswith('experiment_stock_origin_') and f'run="{run["runId"]}"' in line:
                stock_origin[line.split('{', 1)[0].removeprefix('experiment_stock_origin_')] = float(line.rsplit(' ', 1)[1])
            if line.startswith('experiment_origin_') and f'run="{run["runId"]}"' in line:
                name = line.split('{', 1)[0].removeprefix('experiment_origin_')
                origin[name] = float(line.rsplit(' ', 1)[1])
    if 'loads_total' not in origin or 'maximum' not in origin:
        raise ValueError(f'Original-load metrics missing: {folder}')
    metrics = json.loads((folder / 'summary.json').read_text())['metrics']
    windows = []
    for name, metric in metrics.items():
        match = re.fullmatch(r'client_window_(\d+)_latency_ms', name)
        if not match:
            continue
        index = int(match.group(1))
        success = metrics[f'client_window_{index}_success']['values']
        windows.append({'startSecond': index, 'latencyMs': metric['values'], 'success': success})
    windows.sort(key=lambda w: w['startSecond'])
    if len(windows) != run['seconds']:
        raise ValueError(f'Expected one-second windows for entire run: {folder}')
    cache = json.loads((folder / 'cache-state.json').read_text())
    delta = {key: value - cache['before']['redisStats'][key] for key, value in cache['after']['redisStats'].items()}
    summary = {'runId': run['runId'], 'phase': run['phase'], 'original': origin, 'stockOriginal': stock_origin,
               'oneSecondWindows': windows, 'windowsAbove200Ms': sum(w['latencyMs']['p(99)'] > 200 for w in windows),
               'p99LimitMs': run.get('p99LimitMs', 200),
               'windowsAboveSlo': sum(w['latencyMs']['p(99)'] > run.get('p99LimitMs', 200) for w in windows),
               'worstWindowP99Ms': max(w['latencyMs']['p(99)'] for w in windows), 'redisStatsDelta': delta,
               'notes': ['Original counters include only this X-Run-ID, not validation/recovery.',
                         'Original concurrency includes DB pool waiting; it is not simultaneous SQL count.',
                         'Redis deltas include validation/recovery and unrelated expirations.',
                         'Windows group requests by start time; no exact expiry-event boundary is claimed.']}
    (folder / 'stampede-observations.json').write_text(json.dumps(summary, indent=2) + '\n')
    return summary


if __name__ == '__main__':
    root = Path(sys.argv[1])
    runs = json.loads((root / 'slo-runs.json').read_text())
    for run in runs:
        summary = summarize(root / 'results' / run['runId'])
        print(json.dumps({k: v for k, v in summary.items() if k != 'oneSecondWindows'}))
