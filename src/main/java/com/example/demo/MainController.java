package com.example.demo;

import com.example.demo.database.DatabaseManager;
import com.example.demo.model.Event;
import com.example.demo.model.User;
import com.example.demo.scheduler.EventShift;
import com.example.demo.scheduler.SchedulingDecision;
import com.example.demo.scheduler.SchedulingDecisionType;
import com.example.demo.scheduler.SmartScheduler;
import com.example.demo.scheduler.TimeSlot;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private GridPane calendarGrid;
    @FXML private Label currentMonthLabel;
    @FXML private TextField titleField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> startTimeCombo;
    @FXML private ComboBox<String> endTimeCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private TextField descriptionField;
    @FXML private TextField locationField;
    @FXML private ListView<Event> eventsListView;
    @FXML private Label selectedDateLabel;
    @FXML private ComboBox<String> participantsCombo;
    @FXML private ListView<String> selectedParticipantsList;
    @FXML private CheckBox checkAvailabilityCheck;

    private YearMonth currentYearMonth;
    private LocalDate selectedDate;
    private List<Event> eventsList;
    private DatabaseManager dbManager;
    private SmartScheduler smartScheduler;

    private List<User> allUsers;
    private List<User> selectedParticipants;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        eventsList = new ArrayList<>();
        allUsers = new ArrayList<>();
        selectedParticipants = new ArrayList<>();
        smartScheduler = new SmartScheduler();

        dbManager = new DatabaseManager();
        try {
            dbManager.connect();
            eventsList = dbManager.getAllEvents();
            allUsers = dbManager.getAllUsers();
            if (allUsers.isEmpty()) {
                dbManager.ensureDefaultUsersIfMissing();
                allUsers = dbManager.getAllUsers();
            }
        } catch (SQLException e) {
            showAlert("Database connection error: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }

        initializeTimeComboBoxes();
        initializeParticipantsCombo();
        priorityCombo.setValue("Medium (3)");
        datePicker.setValue(LocalDate.now());

        eventsListView.setPlaceholder(new Label("No events for selected date"));
        eventsListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format(
                            "%s %s - %s | %s",
                            "★".repeat(item.getPriority()),
                            item.getStartTime().toLocalTime(),
                            item.getEndTime().toLocalTime(),
                            item.getTitle()
                    ));
                }
            }
        });

        updateCalendarView();
        selectDate(LocalDate.now());
    }

    private void initializeParticipantsCombo() {
        if (participantsCombo == null) {
            return;
        }

        ObservableList<String> participantNames = FXCollections.observableArrayList();
        for (User user : allUsers) {
            participantNames.add(user.getFullName());
        }

        participantsCombo.setItems(participantNames);
        participantsCombo.setPromptText(allUsers.isEmpty() ? "No participants found in database" : "Choose participant");
    }

    private User findUserByFullName(String fullName) {
        for (User user : allUsers) {
            if (user.getFullName().equals(fullName)) {
                return user;
            }
        }
        return null;
    }

    private void initializeTimeComboBoxes() {
        ObservableList<String> times = FXCollections.observableArrayList();
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 30) {
                times.add(String.format("%02d:%02d", hour, minute));
            }
        }
        startTimeCombo.setItems(times);
        endTimeCombo.setItems(times);
        startTimeCombo.setValue("09:00");
        endTimeCombo.setValue("10:00");

        priorityCombo.setItems(FXCollections.observableArrayList(
                "Highest (5)",
                "High (4)",
                "Medium (3)",
                "Low (2)",
                "Lowest (1)"
        ));
    }

    private void updateCalendarView() {
        calendarGrid.getChildren().clear();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("en", "US"));
        currentMonthLabel.setText(currentYearMonth.format(formatter));

        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setAlignment(Pos.CENTER);
            GridPane.setConstraints(dayLabel, i, 0);
            calendarGrid.getChildren().add(dayLabel);
        }

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth = currentYearMonth.lengthOfMonth();
        int dayCounter = 1;

        for (int row = 1; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                if ((row == 1 && col < dayOfWeek) || dayCounter > daysInMonth) {
                    Pane emptyPane = new Pane();
                    emptyPane.setStyle("-fx-background-color: #EEEEEE;");
                    emptyPane.setPrefSize(140, 80);
                    GridPane.setConstraints(emptyPane, col, row);
                    calendarGrid.getChildren().add(emptyPane);
                } else {
                    VBox dayCell = createDayCell(dayCounter);
                    GridPane.setConstraints(dayCell, col, row);
                    calendarGrid.getChildren().add(dayCell);
                    dayCounter++;
                }
            }
            if (dayCounter > daysInMonth) {
                break;
            }
        }
    }

    private VBox createDayCell(int day) {
        VBox cell = new VBox(3);
        cell.setPrefSize(140, 80);
        cell.setAlignment(Pos.TOP_RIGHT);
        cell.setStyle("-fx-background-color: white; -fx-border-color: #DDDDDD; -fx-border-width: 1; -fx-padding: 5; -fx-cursor: hand;");

        LocalDate cellDate = currentYearMonth.atDay(day);

        Label dayLabel = new Label(String.valueOf(day));
        dayLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        if (cellDate.equals(LocalDate.now())) {
            cell.setStyle(cell.getStyle() + "-fx-background-color: #E3F2FD;");
            dayLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        }

        cell.getChildren().add(dayLabel);

        long count = eventsList.stream()
                .filter(e -> e.getStartTime().toLocalDate().equals(cellDate))
                .count();

        if (count > 0) {
            Label countLabel = new Label("• " + count + " events");
            countLabel.setStyle("-fx-font-size: 10px;");
            cell.getChildren().add(countLabel);
        }

        cell.setOnMouseClicked(e -> selectDate(cellDate));

        return cell;
    }

    private void selectDate(LocalDate date) {
        selectedDate = date;
        datePicker.setValue(date);
        updateEventsList(date);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        selectedDateLabel.setText("Events on " + date.format(formatter));
    }

    private void updateEventsList(LocalDate date) {
        List<Event> selectedDateEvents = eventsList.stream()
                .filter(e -> e.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(Event::getStartTime))
                .toList();

        eventsListView.setItems(FXCollections.observableArrayList(selectedDateEvents));
    }

    @FXML
    private void addParticipant() {
        User selectedUser = findUserByFullName(participantsCombo.getValue());
        if (selectedUser != null && !selectedParticipants.contains(selectedUser)) {
            selectedParticipants.add(selectedUser);
            updateParticipantsList();
            participantsCombo.setValue(null);
        }
    }

    private void updateParticipantsList() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (User user : selectedParticipants) {
            items.add("• " + user.getFullName());
        }
        selectedParticipantsList.setItems(items);
    }

    @FXML
    private void addEvent() {
        try {
            if (titleField.getText().trim().isEmpty()) {
                showAlert("Please enter a title.", Alert.AlertType.WARNING);
                return;
            }

            LocalDate date = datePicker.getValue();
            LocalTime startTime = LocalTime.parse(startTimeCombo.getValue());
            LocalTime endTime = LocalTime.parse(endTimeCombo.getValue());

            if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                showAlert("End time must be after start time.", Alert.AlertType.ERROR);
                return;
            }

            Event newEvent = new Event(
                    0,
                    titleField.getText().trim(),
                    LocalDateTime.of(date, startTime),
                    LocalDateTime.of(date, endTime),
                    getPriorityValue(priorityCombo.getValue()),
                    descriptionField.getText().trim(),
                    locationField.getText().trim()
            );

            if (checkAvailabilityCheck != null && checkAvailabilityCheck.isSelected()) {
                SchedulingDecision decision = smartScheduler.decide(newEvent, eventsList);
                if (!handleDecision(newEvent, decision)) {
                    return;
                }
            }

            if (dbManager.insertEvent(newEvent)) {
                for (User participant : selectedParticipants) {
                    dbManager.addParticipant(newEvent.getId(), participant.getUserId(), false);
                }

                eventsList.add(newEvent);
                showAlert("Event added successfully.", Alert.AlertType.INFORMATION);
                clearForm();
                updateCalendarView();
                updateEventsList(date);
            } else {
                showAlert("Failed to save event.", Alert.AlertType.ERROR);
            }

        } catch (Exception e) {
            showAlert("Error: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean handleDecision(Event newEvent, SchedulingDecision decision) {
        if (decision.getType() == SchedulingDecisionType.NO_CONFLICT) {
            return true;
        }

        if (decision.getType() == SchedulingDecisionType.SHIFT_CONFLICTING_EVENTS) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Smart scheduling suggestion");
            alert.setHeaderText("Conflicts found with lower-priority events.");
            alert.setContentText(buildShiftMessage(decision.getShifts()));

            ButtonType apply = new ButtonType("Apply smart shift");
            ButtonType keep = new ButtonType("Keep new event only");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(apply, keep, cancel);

            Optional<ButtonType> selected = alert.showAndWait();
            if (selected.isEmpty() || selected.get() == cancel) {
                return false;
            }
            if (selected.get() == apply) {
                applyShifts(decision.getShifts());
            }
            return true;
        }

        if (decision.getType() == SchedulingDecisionType.SUGGEST_ALTERNATIVES) {
            List<String> options = new ArrayList<>();
            for (TimeSlot slot : decision.getAlternatives()) {
                options.add(formatSlot(slot));
            }
            options.add("Keep original time");

            ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(options.get(0), options);
            choiceDialog.setTitle("Smart scheduling suggestion");
            choiceDialog.setHeaderText("Conflicts found. Choose preferred action.");
            choiceDialog.setContentText("Suggested slots:");

            Optional<String> selected = choiceDialog.showAndWait();
            if (selected.isEmpty()) {
                return false;
            }

            if (!"Keep original time".equals(selected.get())) {
                TimeSlot selectedSlot = decision.getAlternatives().get(options.indexOf(selected.get()));
                newEvent.setStartTime(selectedSlot.getStart());
                newEvent.setEndTime(selectedSlot.getEnd());
            }
            return true;
        }

        Alert hardConflict = new Alert(Alert.AlertType.WARNING);
        hardConflict.setTitle("Hard conflict");
        hardConflict.setHeaderText("No automatic resolution found.");
        hardConflict.setContentText("Add the event anyway?");

        ButtonType addAnyway = new ButtonType("Add anyway");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        hardConflict.getButtonTypes().setAll(addAnyway, cancel);
        return hardConflict.showAndWait().orElse(cancel) == addAnyway;
    }

    private void applyShifts(List<EventShift> shifts) {
        for (EventShift shift : shifts) {
            Event event = shift.getEvent();
            event.setStartTime(shift.getNewStart());
            event.setEndTime(shift.getNewEnd());
            dbManager.updateEvent(event);
        }
    }

    private String buildShiftMessage(List<EventShift> shifts) {
        StringBuilder sb = new StringBuilder();
        for (EventShift shift : shifts) {
            sb.append("• ")
                    .append(shift.getEvent().getTitle())
                    .append(" -> ")
                    .append(shift.getNewStart().toLocalTime())
                    .append(" - ")
                    .append(shift.getNewEnd().toLocalTime())
                    .append("\n");
        }
        return sb.toString();
    }

    private String formatSlot(TimeSlot slot) {
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        return slot.getStart().toLocalDate() + " "
                + timeFormat.format(slot.getStart().toLocalTime())
                + " - "
                + timeFormat.format(slot.getEnd().toLocalTime());
    }

    @FXML
    private void previousMonth() {
        currentYearMonth = currentYearMonth.minusMonths(1);
        updateCalendarView();
    }

    @FXML
    private void nextMonth() {
        currentYearMonth = currentYearMonth.plusMonths(1);
        updateCalendarView();
    }

    @FXML
    private void goToToday() {
        currentYearMonth = YearMonth.now();
        updateCalendarView();
        selectDate(LocalDate.now());
    }

    @FXML
    private void deleteSelectedEvent() {
        Event selected = eventsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select an event to delete.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete event");
        confirm.setHeaderText("Delete selected event?");
        confirm.setContentText(selected.getTitle());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        if (dbManager.deleteEvent(selected.getId())) {
            eventsList.removeIf(e -> e.getId() == selected.getId());
            updateCalendarView();
            updateEventsList(selectedDate);
            showAlert("Event deleted.", Alert.AlertType.INFORMATION);
        } else {
            showAlert("Failed to delete event from database.", Alert.AlertType.ERROR);
        }
    }

    private void clearForm() {
        titleField.clear();
        descriptionField.clear();
        locationField.clear();
        priorityCombo.setValue("Medium (3)");
        startTimeCombo.setValue("09:00");
        endTimeCombo.setValue("10:00");
        selectedParticipants.clear();
        updateParticipantsList();
        participantsCombo.setValue(null);
    }

    private int getPriorityValue(String text) {
        if (text == null) {
            return 3;
        }
        if (text.contains("(5)")) return 5;
        if (text.contains("(4)")) return 4;
        if (text.contains("(3)")) return 3;
        if (text.contains("(2)")) return 2;
        return 1;
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Smart Diary");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
