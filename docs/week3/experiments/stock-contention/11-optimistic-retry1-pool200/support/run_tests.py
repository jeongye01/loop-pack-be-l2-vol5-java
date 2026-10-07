"""Run unchanged concurrency tests and export their JUnit XML results."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

LAB = Path(__file__).resolve().parent.parent
ROOT = next(p for p in LAB.parents if (p / "gradlew").exists())
RAW = ROOT / "build/serializable-experiment"
RESULT = LAB / "results/test-results.json"
SUITES = ("com.loopers.order.application.OrderConcurrencyTest", "com.loopers.experiment.IsolationProbeTest")


def collect(number, exit_code):
    target = RAW / f"tests-{number:02}"
    target.mkdir(parents=True, exist_ok=True)
    tests = []
    for suite in SUITES:
        source = ROOT / f"apps/commerce-api/build/test-results/test/TEST-{suite}.xml"
        if not source.exists():
            raise RuntimeError(f"Missing fresh JUnit result: {source}")
        shutil.copy2(source, target / source.name)
        xml = ET.parse(source).getroot()
        for case in xml.findall("testcase"):
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            tests.append({"suite": suite, "name": case.attrib["name"],
                          "status": "skipped" if case.find("skipped") is not None else "failed" if failure is not None else "passed",
                          "seconds": float(case.attrib.get("time", 0)),
                          "failure": failure.attrib.get("message") if failure is not None else None,
                          "source": str((target / source.name).relative_to(ROOT))})
    return {"round": number, "recordedAt": datetime.now(timezone.utc).isoformat(),
            "gradleExitCode": exit_code, "tests": tests}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--import-first", action="store_true")
    args = parser.parse_args()
    env = os.environ.copy()
    env["SPRING_APPLICATION_JSON"] = (LAB / "config.json").read_text()
    result = {"schemaVersion": 1, "experimentId": "stock-contention/11-optimistic-retry1-pool200",
              "gitSha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "workingTreeChanges": True, "config": json.loads(env["SPRING_APPLICATION_JSON"]), "rounds": []}
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RAW.mkdir(parents=True, exist_ok=True)
    for number in range(1, args.rounds + 1):
        if number == 1 and args.import_first:
            code = 1
        else:
            # Clear old XML so an infrastructure failure cannot reuse previous results.
            xml_dir = ROOT / "apps/commerce-api/build/test-results/test"
            for suite in SUITES:
                (xml_dir / f"TEST-{suite}.xml").unlink(missing_ok=True)
            with (RAW / f"test-{number:02}.log").open("w") as log:
                process = subprocess.run(["./gradlew", "-I", str(LAB / "support/tests.gradle"),
                    ":apps:commerce-api:test", "--tests", "*OrderConcurrencyTest", "--tests", "*IsolationProbeTest", "--rerun-tasks"],
                    cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=300)
            code = process.returncode
        row = collect(number, code)
        result["rounds"].append(row)
        RESULT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        cases = [x for x in row["tests"] if x["suite"] == SUITES[0]]
        print(f"round {number}: {sum(x['status'] == 'passed' for x in cases)}/4 passed", flush=True)
        probe = [x for x in row["tests"] if x["suite"] == SUITES[1]]
        if len(probe) != 1 or probe[0]["status"] != "passed":
            raise RuntimeError("Actual REPEATABLE-READ connection isolation verification failed")
    print(str(RESULT), flush=True)


if __name__ == "__main__":
    main()
