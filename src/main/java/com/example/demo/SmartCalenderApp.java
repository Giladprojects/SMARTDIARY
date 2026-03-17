package com.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SmartCalenderApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        showLoginView();
        primaryStage.show();
    }

    public static void showLoginView() {
        SessionManager.clear();
        showScene("LoginView.fxml", "Smart Diary Login", 760, 620);
    }

    public static void showMainView() {
        showScene("MainView.fxml", "Smart Diary", 1200, 700);
    }

    private static void showScene(String fxmlName, String title, double width, double height) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(SmartCalenderApp.class.getResource(fxmlName));
            Scene scene = new Scene(fxmlLoader.load(), width, height);
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load scene " + fxmlName, e);
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
