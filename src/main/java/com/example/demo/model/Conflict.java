package com.example.demo.model;

import java.time.LocalDateTime;

public class Conflict {
    private int conflictId;
    private int event1Id;
    private int event2Id;
    private String conflictType;  // "time", "participant", "location"
    private LocalDateTime detectedAt;
    private boolean resolved;
    private String resolution;

    public Conflict(int conflictId, int event1Id, int event2Id, String conflictType,
                    LocalDateTime detectedAt, boolean resolved, String resolution) {
        this.conflictId = conflictId;
        this.event1Id = event1Id;
        this.event2Id = event2Id;
        this.conflictType = conflictType;
        this.detectedAt = detectedAt;
        this.resolved = resolved;
        this.resolution = resolution;
    }

    // Getters and Setters
    public int getConflictId() { return conflictId; }
    public void setConflictId(int conflictId) { this.conflictId = conflictId; }

    public int getEvent1Id() { return event1Id; }
    public void setEvent1Id(int event1Id) { this.event1Id = event1Id; }

    public int getEvent2Id() { return event2Id; }
    public void setEvent2Id(int event2Id) { this.event2Id = event2Id; }

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
