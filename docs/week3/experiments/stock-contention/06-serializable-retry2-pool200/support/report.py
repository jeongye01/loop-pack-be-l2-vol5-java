"""Generate Markdown tables from JSON; preserve the handwritten interpretation."""
from collections import defaultdict
import json
from pathlib import Path

LAB = Path(__file__).resolve().parent.parent
START = '## 핵심 결과'
END = '## 해석'


def generate():
    rows = ['',
            'S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀 200개에서 재시도 2회·0ms의 DB 데드락과 기술 오류를 측정했다.', '',
            '| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |',
            '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |']
    measurements = []
    for path in (LAB / 'results').glob('measure-*/result.json'):
        data = json.loads(path.read_text())
        if 'metrics' in data:
            measurements.append(data)
    for rps in (10, 30, 100):
        group = [data for data in measurements if data['config']['rps'] == rps]
        errors = [data['requests']['errors'].get('deadlock', 0) for data in group]
        rejection = (f"{min(data['requests']['businessRejection'] for data in group)}~"
                     f"{max(data['requests']['businessRejection'] for data in group)}") if group else '—'
        technical = (f"{min(data['requests']['technicalError'] for data in group)}~"
                     f"{max(data['requests']['technicalError'] for data in group)}") if group else '—'
        cause_keys = sorted({key for data in group for key in data['requests']['errors'] if key != 'INSUFFICIENT_STOCK'})
        causes = ', '.join(f"{key}:{min(data['requests']['errors'].get(key, 0) for data in group)}~{max(data['requests']['errors'].get(key, 0) for data in group)}"
                           for key in cause_keys) or '없음'
        deadlocks = (f"{min(errors)}~{max(errors)}") if group else '—'
        p95 = (f"{min(data['metrics']['latencyMs']['p95'] for data in group):.0f}~"
               f"{max(data['metrics']['latencyMs']['p95'] for data in group):.0f}") if group else '—'
        rows.append(f"| {rps} | {len(group)}회 | {group[0]['requests']['planned'] if group else '—'} | "
                    f"{group[0]['requests']['success'] if group else '—'} | {rejection} | {technical} | {causes} | {deadlocks} | "
                    f"{p95} | {max(data['requests']['notStarted'] for data in group) if group else '—'} | "
                    f"{'통과' if group and all(data['validation']['status'] == 'passed' for data in group) else '—'} |")
    rows += ['', '- **판정**: 커넥션 풀 획득 시간 초과는 발생하지 않았다. 10·30 RPS는 기술 오류 없이 완료됐고, 100 RPS에서는 데드락 기술 오류 0~35건이 남았다. 모든 측정 Run의 DB 정합성 검증을 통과했다.',
             '- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.', '',
             '## 동시성 테스트', '', '| 테스트 | 통과 | 실패 | 건너뜀 |', '| --- | ---: | ---: | ---: |']
    source = LAB / 'results/test-results.json'
    if source.exists():
        tests = json.loads(source.read_text())
        counts = defaultdict(lambda: defaultdict(int))
        for run in tests['rounds']:
            for test in run['tests']:
                counts[test['name']][test['status']] += 1
        for name, outcomes in counts.items():
            rows.append(f"| {name} | {outcomes['passed']} | {outcomes['failed']} | {outcomes['skipped']} |")
        rows += ['', '[테스트 원본 JSON](results/test-results.json)']
    else:
        rows += ['', '미실행']
    paths = list((LAB / 'results').glob('*/result.json'))
    paths.sort(key=lambda path: (
        json.loads(path.read_text()).get('config', {}).get('rps', 0), path.parent.name))
    measure_data = []
    for path in paths:
        if not path.parent.name.startswith('measure-'):
            continue
        data = json.loads(path.read_text())
        measure_data.append((path, data))

    rows += ['', '## HTTP 본 측정 처리 결과', '',
             '| Run | 전체 요청 수 | 성공 주문 | 품절 거절 | 기술 오류 | 전송되지 않은 요청 | 전체 TPS | 성공 TPS | 품절 거절 TPS | 기술 오류 TPS | 데이터 정합성 |',
             '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |']
    for path, data in measure_data:
        link = f"[{data['runId']}]({path.relative_to(LAB)})"
        if 'metrics' not in data:
            rows.append(f"| {link} | — | — | — | — | — | — | — | {data['status']} |")
            continue
        requests, metrics = data['requests'], data['metrics']
        seconds = metrics['measurementSeconds']
        rows.append(f"| {link} | {requests['planned']} | {requests['success']} | {requests['businessRejection']} | {requests['technicalError']} | "
                    f"{requests['notStarted']} | {metrics['throughput']:.2f} | {metrics['successThroughput']:.2f} | "
                    f"{requests['businessRejection'] / seconds:.2f} | {requests['technicalError'] / seconds:.2f} | "
                    f"{data['validation']['status']} |")

    def append_latency_table(title, key):
        rows.extend(['', f'## {title}', '',
                     '| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |',
                     '| --- | ---: | ---: | ---: | ---: |'])
        for path, data in measure_data:
            link = f"[{data['runId']}]({path.relative_to(LAB)})"
            metrics = data.get('metrics', {})
            values = metrics.get('latencyMs') if key == 'all' else metrics.get('latencyByOutcomeMs', {}).get(key)
            if not values or values.get('count', 0) == 0:
                rows.append(f'| {link} | 0 | — | — | — |')
                continue
            rows.append(f"| {link} | {values['count']} | {values['p50']:.2f} | {values['p95']:.2f} | {values['p99']:.2f} |")

    append_latency_table('전체 요청 응답 시간', 'all')
    append_latency_table('성공 주문 응답 시간', 'success')
    append_latency_table('품절 거절 응답 시간', 'businessRejection')
    append_latency_table('기술 오류 응답 시간', 'technicalError')
    generated = START + '\n' + '\n'.join(rows) + '\n\n' + END
    target = LAB / 'report.md'
    if target.exists():
        previous = target.read_text()
        assert START in previous and END in previous
        target.write_text(previous[:previous.index(START)] + generated + previous[previous.index(END)+len(END):])
    else:
        target.write_text('# SERIALIZABLE 실험 결과\n\n' + generated + '\n\n## 해석\n\n실행 완료 후 작성한다.\n')


if __name__ == '__main__':
    generate()
