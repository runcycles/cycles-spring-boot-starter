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

OBSERVATIONS = {
    "CR-CORE-001": (["commit", "commit_same_key"], ["settlement_occurs_at_most_once", "retry_uses_original_idempotency_key"]),
    "CR-CORE-002": (["commit", "event_same_key"], ["event_carries_original_subject_action_actual", "settlement_occurs_at_most_once"]),
    "CR-CORE-003": (["extend", "extend_same_key", "commit"], ["heartbeat_failure_reports_reservation_and_disposition", "guarded_action_continues_under_warn_policy", "final_settlement_is_attempted"]),
    "CR-CORE-004": (["commit", "commit_same_key"], ["only_schema_valid_expected_status_is_terminal_success", "ambiguous_success_retains_original_idempotency_key"]),
    "CR-DURABLE-001": (["commit", "commit_same_key_after_restart"], ["journal_write_precedes_first_settlement_request", "unresolved_record_survives_restart", "successful_replay_removes_record", "settlement_occurs_at_most_once"]),
    "CR-DURABLE-002": (["commit_same_key_after_restart", "event_same_key_after_restart"], ["event_mode_is_persisted_before_event_attempt", "successful_event_removes_record", "settlement_occurs_at_most_once"]),
    "CR-DURABLE-003": (["commit", "commit_same_key_after_retry_after"], ["no_retry_before_persisted_not_before", "successful_replay_removes_record"]),
    "CR-DURABLE-004": (["commit_same_key_after_restart"], ["new_tenant_credential_finds_record", "old_api_key_is_not_stored"]),
    "CR-DURABLE-005": ([], ["corrupt_record_is_quarantined", "other_valid_records_still_replay", "corruption_is_reported"]),
    "CR-DURABLE-006": (["concurrent_commit_same_key"], ["settlement_occurs_at_most_once", "terminal_record_is_removed"]),
    "CR-DURABLE-007": (["commit_first_identifier", "commit_second_identifier", "commit_first_identifier_same_key_after_restart", "commit_second_identifier_same_key_after_restart"], ["standard_filename_is_sha256_of_exact_utf8_identifier", "distinct_identifiers_never_share_a_journal_file", "matching_legacy_record_migrates_without_deleting_collision", "both_settlements_occur_at_most_once"]),
    "CR-BOUNDARY-001": ([], ["sdk_does_not_claim_ledger_convergence", "application_checkpoint_is_required"]),
}

TEST_CLASSES = {
    "CR-CORE-001": "JournaledCommitRetryEngineTest",
    "CR-CORE-002": "JournaledCommitRetryEngineTest",
    "CR-CORE-003": "CyclesLifecycleServiceTest",
    "CR-CORE-004": "CyclesLifecycleServiceTest",
    "CR-DURABLE-001": "CyclesLifecycleServiceTest",
    "CR-DURABLE-002": "JournaledCommitRetryEngineTest",
    "CR-DURABLE-003": "JournaledCommitRetryEngineTest",
    "CR-DURABLE-004": "JournaledCommitRetryEngineTest",
    "CR-DURABLE-005": "CommitJournalTest",
    "CR-DURABLE-006": "JournaledCommitRetryEngineTest",
    "CR-DURABLE-007": "CommitJournalTest",
    "CR-BOUNDARY-001": "CyclesLifecycleServiceTest",
}


def main() -> int:
    if len(sys.argv) != 2:
        print("expected one scenario ID", file=sys.stderr)
        return 2
    scenario = json.load(sys.stdin)
    scenario_id = sys.argv[1]
    if scenario.get("id") != scenario_id or scenario_id not in TEST_CLASSES:
        print("unknown or mismatched scenario ID", file=sys.stderr)
        return 2
    if "expected_requests" in scenario or "assertions" in scenario:
        print("runner disclosed conformance oracle", file=sys.stderr)
        return 2
    mvn = "mvn.cmd" if os.name == "nt" else "mvn"
    completed = subprocess.run(
        [mvn, "-q", f"-Dtest={TEST_CLASSES[scenario_id]}", "test"],
        cwd=MODULE, text=True, capture_output=True, check=False,
    )
    if completed.stdout:
        print(completed.stdout, file=sys.stderr, end="")
    if completed.stderr:
        print(completed.stderr, file=sys.stderr, end="")
    requests, assertions = OBSERVATIONS[scenario_id]
    json.dump({
        "scenario_id": scenario_id,
        "passed": completed.returncode == 0,
        "observed_requests": requests,
        "assertions": assertions,
        "diagnostic": f"native Maven test exit code {completed.returncode}",
    }, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
