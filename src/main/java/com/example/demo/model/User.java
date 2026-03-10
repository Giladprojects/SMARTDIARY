package com.example.demo.model;

public class User {
    private int userId;
    private String username;
    private String fullName;
    private String email;

    public User(int userId, String username, String fullName, String email) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
