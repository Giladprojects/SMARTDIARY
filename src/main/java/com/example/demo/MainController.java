package com.example.demo;

import com.example.demo.database.DatabaseManager;
import com.example.demo.model.Event;
import com.example.demo.model.RecurringEventSeries;
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
    @FXML private ComboBox<String> recurrenceCombo;
    @FXML private ComboBox<String> repeatUntilCombo;
    @FXML private TextField repeatCountField;
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
        } catch (SQLException | RuntimeException e) {
            showAlert("Database connection error: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }

        initializeTimeComboBoxes();
        initializeRecurrenceControls();
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
                    String stars = "*".repeat(Math.max(1, item.getPriority()));
                    String recurringMarker = item.getRecurrenceId() == null ? "" : " [Recurring]";
                    setText(String.format(
                            "%s %s - %s | %s%s",
                            stars,
                            item.getStartTime().toLocalTime(),
                            item.getEndTime().toLocalTime(),
                            item.getTitle(),
                            recurringMarker
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
            participantNames.add(formatUserOption(user));
        }

        participantsCombo.setItems(participantNames);
        participantsCombo.setPromptText(allUsers.isEmpty() ? "No participants found in database" : "Choose participant");
    }

    private String formatUserOption(User user) {
        return user.getFullName() + " (" + user.getUsername() + ")";
    }

    private User findUserByOption(String selectedOption) {
        if (selectedOption == null || selectedOption.isBlank()) {
            return null;
        }

        for (User user : allUsers) {
            if (formatUserOption(user).equals(selectedOption)) {
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

    private void initializeRecurrenceControls() {
        if (recurrenceCombo == null || repeatUntilCombo == null || repeatCountField == null) {
            return;
        }

        recurrenceCombo.setItems(FXCollections.observableArrayList(
                "Does not repeat",
                "Every day",
                "Every week",
                "Every month"
        ));
        recurrenceCombo.setValue("Does not repeat");

        repeatUntilCombo.setItems(FXCollections.observableArrayList(
                "Forever",
                "Days",
                "Weeks",
                "Months"
        ));
        repeatUntilCombo.setValue("Forever");

        recurrenceCombo.valueProperty().addListener((ignored, oldValue, newValue) -> updateRecurrenceInputs());
        repeatUntilCombo.valueProperty().addListener((ignored, oldValue, newValue) -> updateRecurrenceInputs());
        updateRecurrenceInputs();
    }

    private void updateRecurrenceInputs() {
        boolean recurring = isRecurringSelection();
        repeatUntilCombo.setDisable(!recurring);
        boolean countRequired = recurring && !"Forever".equals(repeatUntilCombo.getValue());
        repeatCountField.setDisable(!countRequired);
        if (!countRequired) {
            repeatCountField.clear();
        }
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

        for (int row = 1; row < 7 && dayCounter <= daysInMonth; row++) {
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
            Label countLabel = new Label("* " + count + " events");
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
        User selectedUser = findUserByOption(participantsCombo.getValue());
        if (selectedUser != null && !selectedParticipants.contains(selectedUser)) {
            selectedParticipants.add(selectedUser);
            updateParticipantsList();
            participantsCombo.setValue(null);
        }
    }

    private void updateParticipantsList() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (User user : selectedParticipants) {
            items.add("* " + user.getFullName());
        }
        selectedParticipantsList.setItems(items);
    }

    @FXML
    private void addEvent() {
        try {
            Event newEvent = buildEventFromForm();
            if (isRecurringSelection()) {
                addRecurringEvent(newEvent);
                return;
            }
            addSingleEvent(newEvent);

        } catch (Exception e) {
            showAlert("Error: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private Event buildEventFromForm() {
        if (titleField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a title.");
        }

        LocalDate date = datePicker.getValue();
        String startTimeValue = startTimeCombo.getValue();
        String endTimeValue = endTimeCombo.getValue();
        if (date == null || startTimeValue == null || endTimeValue == null) {
            throw new IllegalArgumentException("Please choose a date, start time, and end time.");
        }

        LocalTime startTime = LocalTime.parse(startTimeValue);
        LocalTime endTime = LocalTime.parse(endTimeValue);

        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        return new Event(
                0,
                titleField.getText().trim(),
                LocalDateTime.of(date, startTime),
                LocalDateTime.of(date, endTime),
                getPriorityValue(priorityCombo.getValue()),
                descriptionField.getText().trim(),
                locationField.getText().trim()
        );
    }

    private void addSingleEvent(Event newEvent) {
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
            updateEventsList(newEvent.getStartTime().toLocalDate());
        } else {
            showAlert("Failed to save event.", Alert.AlertType.ERROR);
        }
    }

    private void addRecurringEvent(Event baseEvent) {
        RecurringEventSeries series = buildRecurringSeries(baseEvent);
        List<Event> occurrences = series.buildOccurrences(resolveRecurringPreviewEnd(series));

        if (checkAvailabilityCheck != null && checkAvailabilityCheck.isSelected()
                && !recurringSeriesFitsWithoutConflicts(occurrences)) {
            return;
        }

        int insertedCount = dbManager.createRecurringEventSeries(series, selectedParticipants);
        if (insertedCount < 0) {
            showAlert("Failed to save recurring event series.", Alert.AlertType.ERROR);
            return;
        }

        eventsList = dbManager.getAllEvents();
        showAlert("Recurring event created. " + insertedCount + " occurrences added.", Alert.AlertType.INFORMATION);
        clearForm();
        updateCalendarView();
        updateEventsList(baseEvent.getStartTime().toLocalDate());
    }

    private RecurringEventSeries buildRecurringSeries(Event baseEvent) {
        String frequency = mapFrequency(recurrenceCombo.getValue());
        LocalDateTime untilDate = resolveRepeatUntil(baseEvent.getStartTime());
        return new RecurringEventSeries(
                0,
                baseEvent.getTitle(),
                baseEvent.getStartTime(),
                baseEvent.getEndTime(),
                baseEvent.getPriority(),
                baseEvent.getDescription(),
                baseEvent.getLocation(),
                frequency,
                untilDate
        );
    }

    private LocalDateTime resolveRepeatUntil(LocalDateTime startTime) {
        String repeatMode = repeatUntilCombo.getValue();
        if ("Forever".equals(repeatMode)) {
            return null;
        }

        int count;
        try {
            count = Integer.parseInt(repeatCountField.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid repeat count.");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Repeat count must be greater than zero.");
        }

        return switch (repeatMode) {
            case "Days" -> startTime.plusDays(count).minusDays(1);
            case "Weeks" -> startTime.plusWeeks(count).minusDays(1);
            case "Months" -> startTime.plusMonths(count).minusDays(1);
            default -> throw new IllegalArgumentException("Choose how long the recurring event should last.");
        };
    }

    private LocalDateTime resolveRecurringPreviewEnd(RecurringEventSeries series) {
        if (series.getUntilDate() != null) {
            return series.getUntilDate();
        }

        LocalDateTime previewEnd = LocalDateTime.now();
        if (previewEnd.isBefore(series.getStartTime())) {
            previewEnd = series.getStartTime();
        }
        return previewEnd.plusMonths(12);
    }

    private boolean recurringSeriesFitsWithoutConflicts(List<Event> occurrences) {
        List<Event> workingSchedule = new ArrayList<>(eventsList);
        for (Event occurrence : occurrences) {
            SchedulingDecision decision = smartScheduler.decide(occurrence, workingSchedule);
            if (decision.getType() != SchedulingDecisionType.NO_CONFLICT) {
                showAlert(
                        "Recurring event conflicts on "
                                + occurrence.getStartTime().toLocalDate()
                                + " at "
                                + occurrence.getStartTime().toLocalTime()
                                + ". Adjust the series or turn off smart conflict checks.",
                        Alert.AlertType.WARNING
                );
                return false;
            }
            workingSchedule.add(occurrence);
        }
        return true;
    }

    private boolean isRecurringSelection() {
        return recurrenceCombo != null && recurrenceCombo.getValue() != null
                && !"Does not repeat".equals(recurrenceCombo.getValue());
    }

    private String mapFrequency(String value) {
        return switch (value) {
            case "Every week" -> RecurringEventSeries.WEEKLY;
            case "Every month" -> RecurringEventSeries.MONTHLY;
            default -> RecurringEventSeries.DAILY;
        };
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
            sb.append("* ")
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

        if (selected.getRecurrenceId() != null) {
            Alert confirmRecurring = new Alert(Alert.AlertType.CONFIRMATION);
            confirmRecurring.setTitle("Delete recurring event");
            confirmRecurring.setHeaderText("Delete the whole recurring series?");
            confirmRecurring.setContentText(selected.getTitle());

            if (confirmRecurring.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            if (dbManager.deleteRecurringSeries(selected.getRecurrenceId())) {
                eventsList = dbManager.getAllEvents();
                updateCalendarView();
                updateEventsList(selectedDate);
                showAlert("Recurring series deleted.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Failed to delete recurring series.", Alert.AlertType.ERROR);
            }
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
        recurrenceCombo.setValue("Does not repeat");
        repeatUntilCombo.setValue("Forever");
        repeatCountField.clear();
        startTimeCombo.setValue("09:00");
        endTimeCombo.setValue("10:00");
        selectedParticipants.clear();
        updateParticipantsList();
        participantsCombo.setValue(null);
        updateRecurrenceInputs();
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
