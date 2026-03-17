package com.example.demo;

import com.example.demo.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static void clear() {
        currentUser = null;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}
