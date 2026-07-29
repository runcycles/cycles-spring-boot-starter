package io.runcycles.client.java.spring.journal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CommitJournal")
class CommitJournalTest {

    private static final String BASE_URL = "http://localhost:7878";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dir;

    private CommitJournal journal() {
        return new CommitJournal(dir);
    }

    private static PendingCommitRecord commitRecord(String reservationId, String baseUrl, Long notBeforeMs) {
        return new PendingCommitRecord(reservationId, baseUrl, PendingCommitRecord.MODE_COMMIT,
                Map.of("idempotency_key", "com-1", "actual", Map.of("unit", "TOKENS", "amount", 5)),
                Map.of("idempotency_key", "com-1", "subject", Map.of("tenant", "t1")),
                System.currentTimeMillis(), notBeforeMs);
    }

    // ========================================================================
    // Auth fingerprint (cross-SDK pinned vectors)
    // ========================================================================

    @Nested
    @DisplayName("authFingerprint")
    class AuthFingerprint {

        @Test
        void shouldMatchPinnedKeyPrincipalVector() {
            // Pinned across SDKs: byte-compatible with cycles-client-python / -typescript
            assertThat(CommitJournal.authFingerprint("http://localhost", "test-key", null))
                    .isEqualTo("68c905017df7dbfc");
        }

        @Test
        void shouldMatchPinnedTenantPrincipalVector() {
            assertThat(CommitJournal.authFingerprint("http://localhost", "any-key", "acme"))
                    .isEqualTo("8baba538fb970da4");
        }

        @Test
        void shouldBeStableAcrossApiKeyRotationWhenTenantSet() {
            String before = CommitJournal.authFingerprint(BASE_URL, "old-key", "acme");
            String after = CommitJournal.authFingerprint(BASE_URL, "new-key", "acme");
            assertThat(after).isEqualTo(before);
        }

        @Test
        void shouldTreatBlankTenantAsAbsent() {
            assertThat(CommitJournal.authFingerprint("http://localhost", "test-key", "  "))
                    .isEqualTo("68c905017df7dbfc");
        }

        @Test
        void shouldScopeByServerAndPrincipal() {
            String keyA = CommitJournal.authFingerprint(BASE_URL, "key-a", null);
            String keyB = CommitJournal.authFingerprint(BASE_URL, "key-b", null);
            String otherServer = CommitJournal.authFingerprint("http://other:7878", "key-a", null);
            String tenantA = CommitJournal.authFingerprint(BASE_URL, "key-a", "acme");
            String tenantB = CommitJournal.authFingerprint(BASE_URL, "key-a", "globex");

            assertThat(keyA).isNotEqualTo(keyB);
            assertThat(keyA).isNotEqualTo(otherServer);
            assertThat(tenantA).isNotEqualTo(tenantB);
            assertThat(tenantA).isNotEqualTo(keyA);
        }

        @Test
        void shouldReturnCachedValueOnRepeatCall() {
            String first = CommitJournal.authFingerprint(BASE_URL, "cached-key", null);
            String second = CommitJournal.authFingerprint(BASE_URL, "cached-key", null);
            assertThat(second).isEqualTo(first).hasSize(16);
        }
    }

    // ========================================================================
    // Record / load roundtrip
    // ========================================================================

    @Nested
    @DisplayName("Record and load")
    class RecordAndLoad {

        @Test
        void shouldRoundtripAllFieldsIncludingNotBefore() {
            long notBefore = System.currentTimeMillis() + 60_000;
            journal().record(commitRecord("res-1", BASE_URL, notBefore));

            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(loaded).hasSize(1);
            PendingCommitRecord entry = loaded.get(0);
            assertThat(entry.getReservationId()).isEqualTo("res-1");
            assertThat(entry.getBaseUrl()).isEqualTo(BASE_URL);
            assertThat(entry.getMode()).isEqualTo("commit");
            assertThat(entry.getCommitBody()).containsEntry("idempotency_key", "com-1");
            assertThat(entry.getEventFallbackBody()).containsKey("subject");
            assertThat(entry.getRecordedAtMs()).isPositive();
            assertThat(entry.getNotBeforeMs()).isEqualTo(notBefore);
        }

        @Test
        void shouldWriteCrossSdkSnakeCaseKeys() throws IOException {
            journal().record(commitRecord("res-keys", BASE_URL, 123L));

            String raw = Files.readString(
                    dir.resolve(CommitJournal.safeName("res-keys") + ".json"));
            Map<String, Object> data = MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
            assertThat(data.keySet()).containsExactly(
                    "version", "reservation_id", "base_url", "mode",
                    "commit_body", "event_fallback_body", "recorded_at_ms", "not_before_ms");
            assertThat(data).containsEntry("version", 1);
        }

        @Test
        void shouldOverwriteExistingRecordForSameReservation() {
            journal().record(commitRecord("res-ow", BASE_URL, null));
            journal().record(commitRecord("res-ow", BASE_URL, 42L));

            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getNotBeforeMs()).isEqualTo(42L);
        }

        @Test
        void shouldFilterByBaseUrl() {
            journal().record(commitRecord("res-mine", BASE_URL, null));
            journal().record(commitRecord("res-other", "http://other:9999", null));

            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getReservationId()).isEqualTo("res-mine");
        }

        @Test
        void shouldUseCrossSdkDigestNamesForExactUtf8ReservationIds() {
            journal().record(commitRecord("res/1:2..x", BASE_URL, null));

            Path standard = dir.resolve(CommitJournal.safeName("res/1:2..x") + ".json");
            assertThat(Files.exists(standard)).isTrue();
            assertThat(CommitJournal.safeName("r🚀"))
                    .isEqualTo("v2-34c5b33347a139e63c81ea72943cc15dd4c2087dc1eaa756a78f3c49974e0b87");
            assertThat(journal().loadPending(BASE_URL)).hasSize(1);

            journal().discard("res/1:2..x");
            assertThat(Files.exists(standard)).isFalse();
        }

        @Test
        void shouldKeepCollidingLegacyIdsDistinctAndMigrateSafely() throws IOException {
            journal().record(commitRecord("rsv/a", BASE_URL, null));
            journal().record(commitRecord("rsv_a", BASE_URL, null));
            assertThat(CommitJournal.safeName("rsv/a"))
                    .isNotEqualTo(CommitJournal.safeName("rsv_a"));

            Path legacy = dir.resolve("rsv_a.json");
            Files.writeString(legacy, commitRecord("rsv/a", BASE_URL, null).toJson());
            journal().discard("rsv_a");
            assertThat(legacy).exists();
            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(legacy).doesNotExist();
            assertThat(dir.resolve(CommitJournal.safeName("rsv/a") + ".json")).exists();
            assertThat(loaded).extracting(PendingCommitRecord::getReservationId)
                    .containsExactly("rsv/a");
        }

        @Test
        void shouldLeaveNoTempFilesBehind() throws IOException {
            journal().record(commitRecord("res-tmp", BASE_URL, null));

            try (var stream = Files.list(dir)) {
                assertThat(stream.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
            }
        }

        @Test
        void shouldIgnoreStaleForeignTempFiles() throws IOException {
            Files.writeString(dir.resolve("res-x.json.9999.deadbeef.tmp"), "partial garbage");
            journal().record(commitRecord("res-x", BASE_URL, null));

            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getReservationId()).isEqualTo("res-x");
            // The stale temp file is not picked up nor quarantined
            assertThat(Files.exists(dir.resolve("res-x.json.9999.deadbeef.tmp"))).isTrue();
        }

        @Test
        void shouldReturnEmptyWhenDirectoryMissing() {
            CommitJournal missing = new CommitJournal(dir.resolve("does-not-exist"));
            assertThat(missing.loadPending(BASE_URL)).isEmpty();
        }
    }

    // ========================================================================
    // Quarantine
    // ========================================================================

    @Nested
    @DisplayName("Quarantine")
    class Quarantine {

        @Test
        void shouldQuarantineUnparseableFiles() throws IOException {
            Files.writeString(dir.resolve("res-bad.json"), "{not valid json");
            journal().record(commitRecord("res-good", BASE_URL, null));

            List<PendingCommitRecord> loaded = journal().loadPending(BASE_URL);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getReservationId()).isEqualTo("res-good");
            assertThat(Files.exists(dir.resolve("res-bad.json"))).isFalse();
            assertThat(Files.exists(dir.resolve("res-bad.corrupt"))).isTrue();
        }

        @Test
        void shouldQuarantineSemanticallyInvalidRecords() throws IOException {
            // Valid JSON but commit mode without a commit_body
            Files.writeString(dir.resolve("res-sem.json"),
                    "{\"version\":1,\"reservation_id\":\"res-sem\",\"base_url\":\"" + BASE_URL
                            + "\",\"mode\":\"commit\"}");

            assertThat(journal().loadPending(BASE_URL)).isEmpty();
            assertThat(Files.exists(dir.resolve("res-sem.corrupt"))).isTrue();
        }
    }

    // ========================================================================
    // Never-throws guarantees
    // ========================================================================

    @Nested
    @DisplayName("Never throws")
    class NeverThrows {

        @Test
        void recordShouldNotThrowWhenDirectoryBlockedByFile() throws IOException {
            Path blocked = dir.resolve("blocked");
            Files.writeString(blocked, "I am a file, not a directory");
            CommitJournal journal = new CommitJournal(blocked);

            journal.record(commitRecord("res-1", BASE_URL, null));
            journal.discard("res-1");
            assertThat(journal.loadPending(BASE_URL)).isEmpty();
        }

        @Test
        void discardShouldNotThrowForMissingEntry() {
            journal().discard("never-recorded");
        }

        @Test
        void recordShouldCleanUpTempFileWhenMoveFailsForNonAtomicityReason() throws IOException {
            // Block the target path with a non-empty directory: the move fails with a
            // plain IOException (not AtomicMoveNotSupportedException), which must
            // propagate to record()'s failure handling — temp cleanup, no throw —
            // rather than being retried non-atomically.
            Path blockedTarget = dir.resolve(CommitJournal.safeName("res-blk") + ".json");
            Files.createDirectory(blockedTarget);
            Files.writeString(blockedTarget.resolve("child"), "occupied");

            journal().record(commitRecord("res-blk", BASE_URL, null));

            assertThat(Files.isDirectory(blockedTarget)).isTrue();
            try (var stream = Files.list(dir)) {
                assertThat(stream.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
            }
        }
    }

    // ========================================================================
    // Record parse validation
    // ========================================================================

    @Nested
    @DisplayName("PendingCommitRecord parse validation")
    class ParseValidation {

        @Test
        void shouldRejectMissingReservationId() {
            assertThatThrownBy(() -> PendingCommitRecord.fromJson("{\"mode\":\"commit\",\"commit_body\":{}}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservation_id");
        }

        @Test
        void shouldRejectEmptyReservationId() {
            assertThatThrownBy(() -> PendingCommitRecord.fromJson(
                    "{\"reservation_id\":\"\",\"mode\":\"commit\",\"commit_body\":{}}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservation_id");
        }

        @Test
        void shouldRejectUnknownMode() {
            assertThatThrownBy(() -> PendingCommitRecord.fromJson(
                    "{\"reservation_id\":\"r1\",\"mode\":\"banana\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown mode");
        }

        @Test
        void shouldRejectCommitModeWithoutCommitBody() {
            assertThatThrownBy(() -> PendingCommitRecord.fromJson(
                    "{\"reservation_id\":\"r1\",\"mode\":\"commit\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("commit_body");
        }

        @Test
        void shouldRejectEventModeWithoutEventFallbackBody() {
            assertThatThrownBy(() -> PendingCommitRecord.fromJson(
                    "{\"reservation_id\":\"r1\",\"mode\":\"event\",\"commit_body\":{}}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("event_fallback_body");
        }

        @Test
        void shouldDefaultToCommitModeAndEmptyBaseUrl() throws Exception {
            PendingCommitRecord entry = PendingCommitRecord.fromJson(
                    "{\"reservation_id\":\"r1\",\"commit_body\":{\"k\":\"v\"}}");
            assertThat(entry.getMode()).isEqualTo("commit");
            assertThat(entry.getBaseUrl()).isEmpty();
            assertThat(entry.getRecordedAtMs()).isZero();
            assertThat(entry.getNotBeforeMs()).isNull();
        }
    }
}
