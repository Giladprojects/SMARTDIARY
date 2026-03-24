package com.example.demo.model;

import java.time.LocalDateTime;

public class Event {
    public static final int FIXED_PRIORITY = 6;

    private int id;
    private int userId;
    private Integer recurrenceId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int priority; // 1-5
    private String description;
    private String location;
    private SoftTimePreference softTimePreference;

    public Event(int id, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location) {
        this(id, 1, null, title, startTime, endTime, priority, description, location, SoftTimePreference.ANY_TIME);
    }

    public Event(int id, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location,
                 SoftTimePreference softTimePreference) {
        this(id, 1, null, title, startTime, endTime, priority, description, location, softTimePreference);
    }

    public Event(int id, int userId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location) {
        this(id, userId, null, title, startTime, endTime, priority, description, location, SoftTimePreference.ANY_TIME);
    }

    public Event(int id, int userId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location,
                 SoftTimePreference softTimePreference) {
        this(id, userId, null, title, startTime, endTime, priority, description, location, softTimePreference);
    }

    public Event(int id, Integer recurrenceId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location) {
        this(id, 1, recurrenceId, title, startTime, endTime, priority, description, location, SoftTimePreference.ANY_TIME);
    }

    public Event(int id, Integer recurrenceId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location,
                 SoftTimePreference softTimePreference) {
        this(id, 1, recurrenceId, title, startTime, endTime, priority, description, location, softTimePreference);
    }

    public Event(int id, int userId, Integer recurrenceId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location) {
        this(id, userId, recurrenceId, title, startTime, endTime, priority, description, location, SoftTimePreference.ANY_TIME);
    }

    public Event(int id, int userId, Integer recurrenceId, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location,
                 SoftTimePreference softTimePreference) {
        this.id = id;
        this.userId = userId;
        this.recurrenceId = recurrenceId;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priority = priority;
        this.description = description;
        this.location = location;
        this.softTimePreference = softTimePreference == null ? SoftTimePreference.ANY_TIME : softTimePreference;
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Integer getRecurrenceId() { return recurrenceId; }
    public void setRecurrenceId(Integer recurrenceId) { this.recurrenceId = recurrenceId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isImmovable() { return priority == FIXED_PRIORITY; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public SoftTimePreference getSoftTimePreference() { return softTimePreference; }
    public void setSoftTimePreference(SoftTimePreference softTimePreference) {
        this.softTimePreference = softTimePreference == null ? SoftTimePreference.ANY_TIME : softTimePreference;
    }

    @Override
    public String toString() {
        return String.format("%s (%s - %s)",
                title,
                startTime.toLocalTime(),
                endTime.toLocalTime());
    }
}
