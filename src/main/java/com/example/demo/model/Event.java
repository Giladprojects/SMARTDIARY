package com.example.demo.model;

import java.time.LocalDateTime;

public class Event {
    private int id;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int priority; // 1-5
    private String description;
    private String location;

    public Event(int id, String title, LocalDateTime startTime,
                 LocalDateTime endTime, int priority,
                 String description, String location) {
        this.id = id;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priority = priority;
        this.description = description;
        this.location = location;
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Override
    public String toString() {
        return String.format("%s (%s - %s)",
                title,
                startTime.toLocalTime(),
                endTime.toLocalTime());
    }
}
