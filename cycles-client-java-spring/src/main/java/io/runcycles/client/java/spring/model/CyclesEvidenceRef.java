package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Reference to a signed CyclesEvidence envelope emitted for an operation.
 */
public class CyclesEvidenceRef {
    private final String evidenceId;
    private final String cyclesEvidenceUrl;

    private CyclesEvidenceRef(String evidenceId, String cyclesEvidenceUrl) {
        this.evidenceId = evidenceId;
        this.cyclesEvidenceUrl = cyclesEvidenceUrl;
    }

    /**
     * Deserializes an evidence reference from a raw API response map.
     *
     * @param map the {@code cycles_evidence} response object, or {@code null}
     * @return the parsed reference, or {@code null} when the input is {@code null}
     */
    public static CyclesEvidenceRef fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new CyclesEvidenceRef(
                map.get("evidence_id") instanceof String value ? value : null,
                map.get("cycles_evidence_url") instanceof String value ? value : null);
    }

    /**
     * Returns the SHA-256 content identifier of the evidence envelope.
     *
     * @return the evidence identifier
     */
    public String getEvidenceId() {
        return evidenceId;
    }

    /**
     * Returns the absolute URL from which the evidence can be fetched.
     *
     * @return the evidence URL
     */
    public String getCyclesEvidenceUrl() {
        return cyclesEvidenceUrl;
    }

    @Override
    public String toString() {
        return "CyclesEvidenceRef{evidenceId='" + evidenceId + '\''
                + ", cyclesEvidenceUrl='" + cyclesEvidenceUrl + "'}";
    }
}
