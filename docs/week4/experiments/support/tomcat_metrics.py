"""Preserve Tomcat connector labels; missing metrics are not zero."""
import re
import statistics

NAMES = ('tomcat_threads_busy_threads', 'tomcat_threads_current_threads', 'tomcat_threads_config_max_threads')

def parse(text):
    result = {}
    for line in text.splitlines():
        match = re.fullmatch(r'(tomcat_threads_\w+)\{([^}]*)\}\s+([0-9.eE+-]+)', line)
        if not match or match[1] not in NAMES:
            continue
        labels = dict(re.findall(r'(\w+)="([^"]*)"', match[2]))
        connector = labels.get('name')
        if connector:
            result.setdefault(connector, {})[match[1]] = float(match[3])
    return result

def summarize(samples):
    connectors = {}
    for sample in samples:
        for name, metrics in parse(sample['app']['stdout']).items():
            target = connectors.setdefault(name, {})
            for metric, value in metrics.items():
                target.setdefault(metric, []).append(value)
    return {name: {metric: {'count':len(values), 'mean':statistics.mean(values),
                          'min':min(values), 'max':max(values)}
                   for metric, values in metrics.items()} for name, metrics in connectors.items()}
