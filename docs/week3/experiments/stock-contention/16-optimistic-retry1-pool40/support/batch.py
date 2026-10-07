"""Execute the agreed warmup + measurement sequence for a scenario."""
import argparse
from lab import run
from report import generate

parser = argparse.ArgumentParser()
parser.add_argument('--scenario', choices=['S1', 'S2', 'S3'], required=True)
parser.add_argument('--two-item-share', type=float)
parser.add_argument('--vus', type=int, default=200)
args = parser.parse_args()
if args.scenario != 'S1' and args.two_item_share is None:
    parser.error('S2/S3 require an agreed --two-item-share')
for rps in (10, 30, 100):
    for repeat in (1, 2, 3):
        for label, duration in (('warmup', 30), ('measure', 60)):
            result = run(args.scenario, rps, duration, repeat, args.vus,
                         args.two_item_share if args.two_item_share is not None else 0.5, label)
            generate()
            if result['status'] != 'completed':
                raise RuntimeError(f"Inspect incomplete run: {result['runId']}")
            if result['validation']['status'] != 'passed':
                print(f"DB validation failed; preserving result: {result['runId']}", flush=True)
