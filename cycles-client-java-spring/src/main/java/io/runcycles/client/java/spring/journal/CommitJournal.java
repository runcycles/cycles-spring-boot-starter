package io.runcycles.client.java.spring.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable file-per-pending-commit journal.
 *
 * <p>Committed spend must survive process restarts: a commit that fails transiently
 * and only lives in an in-memory retry thread vanishes if the JVM exits, and once
 * the reservation's grace period elapses the server returns the reserved budget to
 * the pool — the ledger under-counts real spend. The journal records every pending
 * commit before the background retry starts and removes it only on a terminal
 * outcome. On the next JVM start the engine replays surviving entries: commit first
 * (idempotent), falling back to {@code POST /v1/events} when the reservation has
 * expired.
 *
 * <p>Each record is one JSON file named after its reservation id, written atomically
 * (unique temp file + atomic move) and deleted on a terminal outcome. One file per
 * commit avoids cross-process file locking; concurrent replay by multiple processes
 * is safe because commit and event requests both carry idempotency keys.
 *
 * <p>Journal I/O is strictly best-effort — a failure to persist must never break the
 * commit path itself. All public methods never throw.
 */
public class CommitJournal {

    private static final Logger LOG = LoggerFactory.getLogger(CommitJournal.class);
    private static final String SUFFIX = ".json";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int FINGERPRINT_CACHE_MAX = 256;
    private static final Map<String, String> FINGERPRINT_CACHE = new ConcurrentHashMap<>();

    private final Path directory;

    /**
     * Creates a journal rooted at the given (per-identity) directory.
     *
     * @param directory the directory holding this identity's journal records
     */
    public CommitJournal(Path directory) {
        this.directory = directory;
    }

    /**
     * Returns the directory holding this journal's records.
     *
     * @return the journal directory
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * Returns the default base location for the pending-commit journal:
     * {@code ~/.runcycles/commit-journal}.
     *
     * @return the default journal base directory
     */
    public static Path defaultJournalDir() {
        return Path.of(System.getProperty("user.home"), ".runcycles", "commit-journal");
    }

    /**
     * Computes a non-secret identity for one (server, principal) pair.
     *
     * <p>Journal records are stored under a per-identity subdirectory so that clients
     * sharing a journal directory but using different servers or principals never
     * replay each other's records. When a tenant is configured it is the principal —
     * stable across API-key rotation, and any same-tenant credential may settle the
     * records. Without a tenant the API key itself is the principal; rotating it then
     * orphans pending records under the old fingerprint (records are plain JSON, so an
     * operator can move them into the new identity directory — replay is idempotent).
     *
     * <p>The truncated PBKDF2-HMAC-SHA256 digest is not reversible, so the fingerprint
     * is safe to use as a directory name. Parameters are fixed constants — the digest
     * must be deterministic across processes, releases, and SDKs (byte-compatible with
     * the Python and TypeScript clients).
     *
     * @param baseUrl the Cycles server base URL
     * @param apiKey  the API key (principal fallback when no tenant is set)
     * @param tenant  the tenant identifier, or {@code null}/blank when not configured
     * @return a 16-hex-character identity fingerprint
     */
    public static String authFingerprint(String baseUrl, String apiKey, String tenant) {
        String principal = tenant != null && !tenant.isBlank() ? "tenant\n" + tenant : "key\n" + apiKey;
        String cacheKey = baseUrl + "\0" + principal;
        String cached = FINGERPRINT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    principal.toCharArray(),
                    ("runcycles-commit-journal\n" + baseUrl).getBytes(StandardCharsets.UTF_8),
                    30_000,
                    256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] digest = factory.generateSecret(spec).getEncoded();
            String fingerprint = HexFormat.of().formatHex(digest).substring(0, 16);
            if (FINGERPRINT_CACHE.size() > FINGERPRINT_CACHE_MAX) {
                FINGERPRINT_CACHE.clear();
            }
            FINGERPRINT_CACHE.put(cacheKey, fingerprint);
            return fingerprint;
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 is mandated on every conformant JRE
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        }
    }

    /**
     * Persists a pending commit. Never throws — a journal failure must not break
     * the commit path (the caller continues without durability).
     *
     * @param entry the record to persist
     */
    public void record(PendingCommitRecord entry) {
        Path tmp = null;
        try {
            Files.createDirectories(directory);
            restrictPermissions(directory, "rwx------");
            String fileName = safeName(entry.getReservationId()) + SUFFIX;
            Path target = directory.resolve(fileName);
            // Unique temp name per writer: concurrent processes may settle the same
            // reservation (replay is idempotent), and a shared temp filename would let
            // one truncate the other mid-write and atomically publish partial JSON.
            tmp = directory.resolve(fileName + "." + ProcessHandle.current().pid()
                    + "." + randomHex8() + ".tmp");
            Files.writeString(tmp, entry.toJson(), StandardCharsets.UTF_8);
            restrictPermissions(tmp, "rw-------");
            atomicMove(tmp, target);
            LOG.debug("Journaled pending commit: id={}, path={}", entry.getReservationId(), target);
        } catch (Exception e) {
            deleteQuietly(tmp);
            LOG.warn("Failed to journal pending commit (continuing without durability): id={}",
                    entry.getReservationId(), e);
        }
    }

    /**
     * Removes a journal entry after a terminal outcome. Never throws.
     *
     * @param reservationId the reservation whose record should be removed
     */
    public void discard(String reservationId) {
        try {
            Files.deleteIfExists(directory.resolve(safeName(reservationId) + SUFFIX));
            Path legacy = directory.resolve(legacySafeName(reservationId) + SUFFIX);
            if (Files.exists(legacy)) {
                try {
                    PendingCommitRecord entry = PendingCommitRecord.fromJson(
                            Files.readString(legacy, StandardCharsets.UTF_8));
                    if (reservationId.equals(entry.getReservationId())) {
                        Files.deleteIfExists(legacy);
                    }
                } catch (Exception ignored) {
                    // Never delete a colliding or malformed legacy record.
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to discard journal entry: id={}", reservationId, e);
        }
    }

    /**
     * Loads surviving entries for the given server. Never throws.
     *
     * <p>Only entries recorded against the same {@code baseUrl} are returned — a
     * journal directory may be shared by processes talking to different servers, and
     * replaying against the wrong one would fail auth. Unparseable files are renamed
     * to {@code *.corrupt} so they surface to operators instead of being retried
     * forever.
     *
     * @param baseUrl the server base URL to filter on
     * @return the surviving records for {@code baseUrl}, oldest file name first
     */
    public List<PendingCommitRecord> loadPending(String baseUrl) {
        List<PendingCommitRecord> entries = new ArrayList<>();
        try {
            if (!Files.isDirectory(directory)) {
                return entries;
            }
            List<Path> paths = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
                stream.forEach(paths::add);
            }
            paths.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path path : paths) {
                PendingCommitRecord entry;
                try {
                    entry = PendingCommitRecord.fromJson(Files.readString(path, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOG.warn("Skipping corrupt journal entry: {}", path, e);
                    quarantine(path);
                    continue;
                }
                Path standardPath = directory.resolve(safeName(entry.getReservationId()) + SUFFIX);
                boolean duplicateOfStandard = false;
                if (!path.equals(standardPath)) {
                    try {
                        if (!Files.exists(standardPath)) {
                            atomicMove(path, standardPath);
                        } else {
                            PendingCommitRecord existing = PendingCommitRecord.fromJson(
                                    Files.readString(standardPath, StandardCharsets.UTF_8));
                            if (entry.getReservationId().equals(existing.getReservationId())) {
                                Files.deleteIfExists(path);
                                duplicateOfStandard = true;
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Could not safely migrate legacy journal filename: id={}, path={}",
                                entry.getReservationId(), path, e);
                    }
                }
                if (duplicateOfStandard) {
                    continue;
                }
                if (entry.getBaseUrl() != null && entry.getBaseUrl().equals(baseUrl)) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to scan commit journal: {}", directory, e);
        }
        return entries;
    }

    /**
     * Computes the standard collision-resistant filename stem from the exact UTF-8
     * reservation identifier.
     *
     * @param reservationId the reservation id
     * @return the sanitized file-name stem
     */
    static String safeName(String reservationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(reservationId.getBytes(StandardCharsets.UTF_8));
            return "v2-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String legacySafeName(String reservationId) {
        return reservationId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems cannot replace atomically; fall back to a plain
            // replace. Any other IOException (permissions, blocked target, ...)
            // propagates to the caller's failure handling — retrying it
            // non-atomically could mask a real fault or publish partial state.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Best-effort permission tightening — records carry spend metadata. No-op on
     * platforms without POSIX modes (e.g. Windows); failure never blocks the write.
     */
    private static void restrictPermissions(Path path, String posixMode) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posixMode));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows / non-POSIX file system — records are still written
        }
    }

    private static void quarantine(Path path) {
        try {
            String name = path.getFileName().toString();
            String stem = name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name;
            Files.move(path, path.resolveSibling(stem + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Failed to quarantine corrupt journal entry: {}", path, e);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String randomHex8() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
