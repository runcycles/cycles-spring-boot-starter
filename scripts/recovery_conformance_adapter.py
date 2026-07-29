#!/usr/bin/env python3
"""Bind shared recovery scenario IDs to native Java SDK behavior tests."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "cycles-client-java-spring"

TESTS = {
    "CR-CORE-001": [
        "JournaledCommitRetryEngineTest#shouldJournalBeforeRetryAndDiscardOnSuccess",
    ],
    "CR-CORE-002": [
        "JournaledCommitRetryEngineTest#shouldPersistEventModeBeforeDeliveringExpiredFallback",
    ],
    "CR-CORE-003": [
        "CyclesLifecycleServiceTest#shouldHandleExtendExceptionGracefully",
    ],
    "CR-CORE-004": [
        "CyclesLifecycleServiceTest#shouldTreatProtocolInvalidCommit2xxAsAmbiguous",
    ],
    "CR-DURABLE-001": [
        "JournaledCommitRetryEngineTest#shouldJournalBeforeRetryAndDiscardOnSuccess",
        "CyclesLifecycleServiceTest#shouldExecuteFullLifecycle",
        "JournaledCommitRetryEngineTest#shouldReplayJournaledCommitOnConstruction",
    ],
    "CR-DURABLE-002": [
        "JournaledCommitRetryEngineTest#shouldPersistEventModeBeforeDeliveringExpiredFallback",
        "JournaledCommitRetryEngineTest#shouldReplayEventModeRecord",
    ],
    "CR-DURABLE-003": [
        "JournaledCommitRetryEngineTest#shouldTreat429AsTransientHonorRetryAfterFloorAndPersistNotBefore",
        "JournaledCommitRetryEngineTest#shouldHonorFutureNotBeforeFloorOnReplay",
    ],
    "CR-DURABLE-004": [
        "JournaledCommitRetryEngineTest#shouldSurviveApiKeyRotationWhenTenantScoped",
    ],
    "CR-DURABLE-005": [
        "CommitJournalTest#shouldQuarantineCorruptAndUnsupportedRecordsWithoutBlockingValidReplay",
    ],
    "CR-DURABLE-006": [
        "JournaledCommitRetryEngineTest#shouldReplayConcurrentlyWithSameKeyAndRemoveRecord",
    ],
    "CR-DURABLE-007": [
        "CommitJournalTest#shouldKeepCollidingLegacyIdsDistinctAndMigrateSafely",
    ],
    "CR-BOUNDARY-001": [
        "CyclesLifecycleServiceTest#shouldThrowWhenActualRequiredButNotProvided",
    ],
}


def main() -> int:
    if len(sys.argv) != 2:
        print("expected one scenario ID", file=sys.stderr)
        return 2
    scenario = json.load(sys.stdin)
    scenario_id = sys.argv[1]
    if scenario.get("id") != scenario_id or scenario_id not in TESTS:
        print("unknown or mismatched scenario ID", file=sys.stderr)
        return 2
    if "expected_requests" in scenario or "assertions" in scenario:
        print("runner disclosed conformance oracle", file=sys.stderr)
        return 2
    mvn = "mvn.cmd" if os.name == "nt" else "mvn"
    executed = []
    passed = True
    last_code = 0
    for test_id in TESTS[scenario_id]:
        completed = subprocess.run(
            [mvn, "-q", f"-Dtest={test_id}", "test"],
            cwd=MODULE, text=True, capture_output=True, check=False,
        )
        executed.append(test_id)
        last_code = completed.returncode
        if completed.stdout:
            print(completed.stdout, file=sys.stderr, end="")
        if completed.stderr:
            print(completed.stderr, file=sys.stderr, end="")
        if completed.returncode != 0:
            passed = False
            break
    json.dump({
        "scenario_id": scenario_id,
        "passed": passed,
        "native_tests": executed,
        "diagnostic": f"native Maven test exit code {last_code}",
    }, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
