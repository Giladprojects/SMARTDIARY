package com.example.demo;

import com.example.demo.database.DatabaseManager;
import com.example.demo.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private TextField registerUsernameField;
    @FXML private TextField registerFullNameField;
    @FXML private TextField registerEmailField;
    @FXML private PasswordField registerPasswordField;
    @FXML private PasswordField registerConfirmPasswordField;
    @FXML private Label authStatusLabel;

    @FXML
    private void login() {
        String username = loginUsernameField.getText() == null ? "" : loginUsernameField.getText().trim();
        String password = loginPasswordField.getText() == null ? "" : loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Enter username and password.");
            return;
        }

        DatabaseManager dbManager = new DatabaseManager();
        try {
            dbManager.connect();
            User user = dbManager.authenticateUser(username, password);
            if (user == null) {
                setStatus("Invalid username or password.");
                return;
            }

            SessionManager.setCurrentUser(user);
            SmartCalenderApp.showMainView();
        } catch (Exception e) {
            showAlert("Login failed: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            dbManager.disconnect();
        }
    }

    @FXML
    private void register() {
        String username = registerUsernameField.getText() == null ? "" : registerUsernameField.getText().trim();
        String fullName = registerFullNameField.getText() == null ? "" : registerFullNameField.getText().trim();
        String email = registerEmailField.getText() == null ? "" : registerEmailField.getText().trim();
        String password = registerPasswordField.getText() == null ? "" : registerPasswordField.getText();
        String confirmPassword = registerConfirmPasswordField.getText() == null ? "" : registerConfirmPasswordField.getText();

        if (username.isEmpty() || fullName.isEmpty()) {
            setStatus("Username and full name are required.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            setStatus("Passwords do not match.");
            return;
        }

        DatabaseManager dbManager = new DatabaseManager();
        try {
            dbManager.connect();
            User user = dbManager.registerUser(username, fullName, email, password);
            SessionManager.setCurrentUser(user);
            SmartCalenderApp.showMainView();
        } catch (Exception e) {
            setStatus(e.getMessage());
        } finally {
            dbManager.disconnect();
        }
    }

    private void setStatus(String message) {
        if (authStatusLabel != null) {
            authStatusLabel.setText(message);
        }
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Smart Diary");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
