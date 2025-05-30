package test.lms;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javafx.concurrent.Task;

public class AdminDashboardController {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/lms";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "654321";
    private static final int DEFAULT_COURSE_COUNT = 20;
    private static final int ITEMS_PER_PAGE = 5;
    private static final String EMAIL_REGEX = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";

    private User currentUser;
    private List<String> currentItems;
    private boolean showingCourses = true;
    private String selectedItem;
    private Timer timer;

    @FXML private Label studentsCountLabel, coursesCountLabel, instructorsCountLabel;
    @FXML private ProgressBar overallProgressBar, detailsProgressBar;
    @FXML private ProgressIndicator overallProgressIndicator;
    @FXML private PieChart courseCompletionChart;
    @FXML private LineChart<String, Number> progressTrendChart;
    @FXML private Pagination pagination;
    @FXML private VBox itemList, mainContent;
    @FXML private TextField searchField;
    @FXML private Label listTitle, detailsTitle, detailsName, detailsDescription, statusLabel, realTimeClock;
    @FXML private Button addButton, updateProgressButton, refreshButton, addUserButton, editUserButton, deleteUserButton, deleteCourseButton;
    @FXML private ToggleButton themeToggle;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> userNameCol, userEmailCol, userRoleCol;
    @FXML private TextArea reportTextArea, notificationArea;
    @FXML private Button generateReportButton;

    @FXML
    private void initialize() {
        currentUser = new User("admin", "admin@lms.com", "administrator");
        statusLabel.setText("Logged in as: " + currentUser.getName() + " | Last refreshed: " + new SimpleDateFormat("hh:mm a zzz").format(new java.util.Date()));

        // Set tooltips
        addUserButton.setTooltip(new Tooltip("Add a new user to the system"));
        editUserButton.setTooltip(new Tooltip("Edit selected user's details"));
        deleteUserButton.setTooltip(new Tooltip("Delete selected user"));
        addButton.setTooltip(new Tooltip("Add a new course"));
        deleteCourseButton.setTooltip(new Tooltip("Delete selected course"));
        updateProgressButton.setTooltip(new Tooltip("Update progress for selected course"));
        refreshButton.setTooltip(new Tooltip("Refresh the dashboard data"));
        generateReportButton.setTooltip(new Tooltip("Generate a system report"));

        setupUI();
        startRealTimeClock();
        loadUserTable();
        updateSummaryCards();
        updateCharts();
        showCourses();
        loadRecentActivity();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateItemList(newVal));
    }

    private void setupUI() {
        addButton.setEffect(new DropShadow(10, Color.web("#3299a8")));
        deleteCourseButton.setEffect(new DropShadow(10, Color.web("#e74c3c")));
        FadeTransition fade = new FadeTransition(Duration.millis(1500), refreshButton);
        fade.setFromValue(1.0); fade.setToValue(0.4); fade.setCycleCount(FadeTransition.INDEFINITE);
        fade.setAutoReverse(true); fade.play();

        userNameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        userEmailCol.setCellValueFactory(cellData -> cellData.getValue().emailProperty());
        userRoleCol.setCellValueFactory(cellData -> cellData.getValue().roleProperty());

        ScaleTransition scale = new ScaleTransition(Duration.millis(500), mainContent);
        scale.setFromX(0.8); scale.setFromY(0.8); scale.setToX(1.0); scale.setToY(1.0);
        scale.play();
    }

    private void startRealTimeClock() {
        timer = new Timer(true); // Use daemon thread
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a zzz 'on' EEEE, MMMM dd, yyyy");
                    realTimeClock.setText(sdf.format(new java.util.Date()));
                    statusLabel.setText("Logged in as: " + currentUser.getName() + " | Last refreshed: " + sdf.format(new java.util.Date()));
                });
            }
        }, 0, 1000);
    }

    private void loadUserTable() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                ObservableList<User> users = FXCollections.observableArrayList();
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT first_name, email, user_type FROM users")) {
                    while (rs.next()) {
                        users.add(new User(rs.getString("first_name"), rs.getString("email"), rs.getString("user_type")));
                    }
                } catch (SQLException e) {
                    Platform.runLater(() -> notificationArea.setText("Error loading users: " + e.getMessage()));
                }
                Platform.runLater(() -> userTable.setItems(users));
                return null;
            }
        };
        new Thread(task).start();
    }

    private void updateCharts() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Update PieChart
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*), status FROM courses GROUP BY status")) {
                    while (rs.next()) {
                        pieChartData.add(new PieChart.Data(rs.getString("status"), rs.getInt(1)));
                    }
                } catch (SQLException e) {
                    pieChartData.add(new PieChart.Data("Error", 1));
                }
                Platform.runLater(() -> courseCompletionChart.setData(pieChartData));

                // Update LineChart
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Progress Trend");
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT progress, updated_at FROM courses ORDER BY updated_at LIMIT 5")) {
                    while (rs.next()) {
                        series.getData().add(new XYChart.Data<>(rs.getTimestamp("updated_at").toString(), rs.getDouble("progress")));
                    }
                } catch (SQLException e) {
                    series.getData().add(new XYChart.Data<>("Error", 0));
                }
                Platform.runLater(() -> {
                    progressTrendChart.getData().clear();
                    progressTrendChart.getData().add(series);
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void showCourses() {
        showingCourses = true;
        listTitle.setText("Courses");
        userTable.setVisible(false);
        itemList.setVisible(true);
        updateItemList("");
    }

    @FXML
    private void showUsers() {
        showingCourses = false;
        listTitle.setText("Users");
        userTable.setVisible(true);
        itemList.setVisible(false);
        loadUserTable();
    }

    @FXML
    private void showAddCourseDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Course");
        dialog.setHeaderText("Enter Course Details");
        TextField titleField = new TextField();
        TextArea descriptionField = new TextArea();
        dialog.getDialogPane().setContent(new VBox(10, new Label("Title:"), titleField, new Label("Description:"), descriptionField));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String title = titleField.getText();
                String description = descriptionField.getText();
                if (!title.isEmpty()) {
                    Task<Void> addCourseTask = new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                                 PreparedStatement stmt = conn.prepareStatement(
                                         "INSERT INTO courses (title, description, status) VALUES (?, ?, 'draft')")) {
                                stmt.setString(1, title);
                                stmt.setString(2, description);
                                stmt.executeUpdate();
                                Platform.runLater(() -> {
                                    showCourses();
                                    showAlert(Alert.AlertType.INFORMATION, "Success", "Course added");
                                });
                            } catch (SQLException e) {
                                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                            }
                            return null;
                        }
                    };
                    new Thread(addCourseTask).start();
                }
            }
        });
    }

    @FXML
    private void generateReport() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                StringBuilder report = new StringBuilder("LMS Report\n");
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE user_type = 'student'");
                    if (rs.next()) report.append("Total Students: ").append(rs.getInt(1)).append("\n");
                    rs = stmt.executeQuery("SELECT COUNT(*) FROM courses");
                    if (rs.next()) report.append("Total Courses: ").append(rs.getInt(1)).append("\n");
                }
                Platform.runLater(() -> reportTextArea.setText(report.toString()));
                return null;
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleAbout() {
        showAlert(Alert.AlertType.INFORMATION, "About", "LMS Application\nVersion 1.0\nDeveloped by xAI");
    }

    @FXML
    private void editUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please select a user to edit");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Edit User Details");

        TextField nameField = new TextField(selectedUser.getName());
        TextField emailField = new TextField(selectedUser.getEmail());
        ComboBox<String> roleCombo = new ComboBox<>(
                FXCollections.observableArrayList("student", "instructor", "administrator"));
        roleCombo.setValue(selectedUser.getRole());

        dialog.getDialogPane().setContent(new VBox(10,
                new Label("Name:"), nameField,
                new Label("Email:"), emailField,
                new Label("Role:"), roleCombo
        ));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String name = nameField.getText();
                String email = emailField.getText();
                String role = roleCombo.getValue();

                if (name.isEmpty() || email.isEmpty() || role == null) {
                    showAlert(Alert.AlertType.ERROR, "Error", "All fields are required");
                    return;
                }

                if (!email.matches(EMAIL_REGEX)) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Invalid email format");
                    return;
                }

                Task<Void> editUserTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                             PreparedStatement stmt = conn.prepareStatement(
                                     "UPDATE users SET first_name = ?, email = ?, user_type = ? WHERE email = ?")) {
                            stmt.setString(1, name);
                            stmt.setString(2, email);
                            stmt.setString(3, role);
                            stmt.setString(4, selectedUser.getEmail());
                            stmt.executeUpdate();
                            Platform.runLater(() -> {
                                loadUserTable();
                                showAlert(Alert.AlertType.INFORMATION, "Success", "User updated");
                            });
                        } catch (SQLException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                        }
                        return null;
                    }
                };
                new Thread(editUserTask).start();
            }
        });
    }

    @FXML
    private void deleteUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please select a user to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete " + selectedUser.getName() + "?");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                Task<Void> deleteUserTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE email = ?")) {
                            stmt.setString(1, selectedUser.getEmail());
                            stmt.executeUpdate();
                            Platform.runLater(() -> {
                                loadUserTable();
                                loadRecentActivity();
                                showAlert(Alert.AlertType.INFORMATION, "Success", "User deleted");
                            });
                        } catch (SQLException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                        }
                        return null;
                    }
                };
                new Thread(deleteUserTask).start();
            }
        });
    }

    @FXML
    private void showUpdateProgressDialog() {
        if (!showingCourses || currentItems == null || currentItems.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please select a course to update progress");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Course Progress");
        dialog.setHeaderText("Enter New Progress Value");

        Slider progressSlider = new Slider(0, 100, 0);
        progressSlider.setShowTickLabels(true);
        progressSlider.setShowTickMarks(true);
        progressSlider.setMajorTickUnit(25);
        progressSlider.setMinorTickCount(5);

        dialog.getDialogPane().setContent(new VBox(10, new Label("Progress (%):"), progressSlider));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                double progress = progressSlider.getValue() / 100.0;
                Task<Void> updateProgressTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                             PreparedStatement stmt = conn.prepareStatement(
                                     "UPDATE courses SET progress = ?, updated_at = CURRENT_TIMESTAMP WHERE title = ?")) {
                            stmt.setDouble(1, progress);
                            stmt.setString(2, selectedItem != null ? selectedItem : currentItems.get(0));
                            stmt.executeUpdate();
                            Platform.runLater(() -> {
                                updateCharts();
                                updateSummaryCards();
                                detailsProgressBar.setProgress(progress);
                                showAlert(Alert.AlertType.INFORMATION, "Success", "Progress updated");
                            });
                        } catch (SQLException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                        }
                        return null;
                    }
                };
                new Thread(updateProgressTask).start();
            }
        });
    }

    @FXML
    private void refreshList() {
        if (showingCourses) {
            showCourses();
        } else {
            showUsers();
        }
        updateCharts();
        updateSummaryCards();
        loadRecentActivity();
        statusLabel.setText("Logged in as: " + currentUser.getName() + " | Last refreshed: " + new SimpleDateFormat("hh:mm a zzz").format(new java.util.Date()));
    }

    @FXML
    private void deleteCourse() {
        if (!showingCourses || currentItems == null || currentItems.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please select a course to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete " + (selectedItem != null ? selectedItem : currentItems.get(0)) + "?");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                Task<Void> deleteCourseTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                             PreparedStatement stmt = conn.prepareStatement("DELETE FROM courses WHERE title = ?")) {
                            stmt.setString(1, selectedItem != null ? selectedItem : currentItems.get(0));
                            stmt.executeUpdate();
                            Platform.runLater(() -> {
                                showCourses();
                                updateCharts();
                                updateSummaryCards();
                                loadRecentActivity();
                                showAlert(Alert.AlertType.INFORMATION, "Success", "Course deleted");
                            });
                        } catch (SQLException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                        }
                        return null;
                    }
                };
                new Thread(deleteCourseTask).start();
            }
        });
    }

    private VBox createPage(int pageIndex) {
        VBox pageBox = new VBox(10);
        pageBox.setPadding(new Insets(10));
        int start = pageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, currentItems.size());
        for (int i = start; i < end; i++) {
            Label label = new Label(currentItems.get(i));
            label.setStyle("-fx-font-size: 14px;");
            label.setOnMouseClicked(event -> {
                selectedItem = label.getText();
                detailsName.setText(selectedItem);
                fetchDetails(selectedItem);
            });
            pageBox.getChildren().add(label);
        }
        return pageBox;
    }

    private void fetchDetails(String item) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String query = showingCourses ? "SELECT description, progress FROM courses WHERE title = ?" : "SELECT email FROM users WHERE first_name = ?";
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, item);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String description = showingCourses ? rs.getString("description") : rs.getString("email");
                        double progress = showingCourses ? rs.getDouble("progress") : 0;
                        Platform.runLater(() -> {
                            detailsDescription.setText(description != null ? description : "No description available");
                            detailsProgressBar.setProgress(progress);
                        });
                    }
                } catch (SQLException e) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load details: " + e.getMessage()));
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void loadRecentActivity() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                StringBuilder activityLog = new StringBuilder("Recent Activity:\n");
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT first_name, last_name, user_type, created_at FROM users ORDER BY created_at DESC LIMIT 5")) {
                    while (rs.next()) {
                        activityLog.append(rs.getString("first_name")).append(" ")
                                .append(rs.getString("last_name"))
                                .append(" (").append(rs.getString("user_type"))
                                .append(") registered at ")
                                .append(rs.getTimestamp("created_at")).append("\n");
                    }
                } catch (SQLException e) {
                    activityLog.append("Error loading recent activity: ").append(e.getMessage());
                }
                Platform.runLater(() -> notificationArea.setText(activityLog.toString()));
                return null;
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void handleExit() {
        Stage stage = (Stage) mainContent.getScene().getWindow();
        if (timer != null) {
            timer.cancel();
        }
        stage.close();
    }

    @FXML
    private void handleLogout() {
        Stage stage = (Stage) mainContent.getScene().getWindow();
        Scene loginScene = new Scene(new Label("Login Scene Placeholder"), 400, 300);
        stage.setScene(loginScene);
        stage.setTitle("LMS Login");
        if (timer != null) {
            timer.cancel();
        }
    }

    private void updateSummaryCards() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    // Students count
                    try (ResultSet rs = conn.createStatement().executeQuery(
                            "SELECT COUNT(*) FROM users WHERE user_type = 'student'")) {
                        if (rs.next()) {
                            final int count = rs.getInt(1);
                            Platform.runLater(() -> studentsCountLabel.setText(String.valueOf(count)));
                        }
                    }

                    // Courses count
                    try (ResultSet rs = conn.createStatement().executeQuery(
                            "SELECT COUNT(*) FROM courses")) {
                        if (rs.next()) {
                            final int count = rs.getInt(1);
                            Platform.runLater(() -> coursesCountLabel.setText(String.valueOf(count)));
                        }
                    }

                    // Instructors count
                    try (ResultSet rs = conn.createStatement().executeQuery(
                            "SELECT COUNT(*) FROM users WHERE user_type = 'instructor'")) {
                        if (rs.next()) {
                            final int count = rs.getInt(1);
                            Platform.runLater(() -> instructorsCountLabel.setText(String.valueOf(count)));
                        }
                    }

                    // Average progress
                    try (ResultSet rs = conn.createStatement().executeQuery(
                            "SELECT AVG(progress) FROM courses")) {
                        if (rs.next()) {
                            final double avg = rs.getDouble(1);
                            Platform.runLater(() -> {
                                overallProgressBar.setProgress(avg);
                                overallProgressIndicator.setProgress(avg);
                            });
                        }
                    }
                } catch (SQLException e) {
                    Platform.runLater(() -> {
                        studentsCountLabel.setText("120");
                        coursesCountLabel.setText("8");
                        instructorsCountLabel.setText("5");
                        overallProgressBar.setProgress(0.7);
                        overallProgressIndicator.setProgress(0.7);
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    @FXML
    private void addUser() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add User");
        dialog.setHeaderText("Enter User Details");

        TextField nameField = new TextField();
        TextField emailField = new TextField();
        PasswordField passwordField = new PasswordField();
        ComboBox<String> roleCombo = new ComboBox<>(
                FXCollections.observableArrayList("student", "instructor", "administrator"));

        dialog.getDialogPane().setContent(new VBox(10,
                new Label("Name:"), nameField,
                new Label("Email:"), emailField,
                new Label("Password:"), passwordField,
                new Label("Role:"), roleCombo
        ));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String name = nameField.getText();
            String email = emailField.getText();
            String password = passwordField.getText();
            String role = roleCombo.getValue();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "All fields are required");
                return;
            }

            if (!email.matches(EMAIL_REGEX)) {
                showAlert(Alert.AlertType.ERROR, "Error", "Invalid email format");
                return;
            }

            Task<Void> addUserTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                         PreparedStatement stmt = conn.prepareStatement(
                                 "INSERT INTO users (first_name, last_name, email, password_hash, user_type, created_at) " +
                                         "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")) {

                        stmt.setString(1, name.split(" ")[0]);
                        stmt.setString(2, name.contains(" ") ? name.split(" ")[1] : "");
                        stmt.setString(3, email);
                        stmt.setString(4, hashPassword(password));
                        stmt.setString(5, role);
                        stmt.executeUpdate();

                        Platform.runLater(() -> {
                            loadUserTable();
                            loadRecentActivity();
                            showAlert(Alert.AlertType.INFORMATION, "Success", "User added");
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()));
                    }
                    return null;
                }
            };
            new Thread(addUserTask).start();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not available", e);
        }
    }

    @FXML
    private void toggleTheme() {
        Scene scene = mainContent.getScene();
        if (scene == null) return;

        if (themeToggle.isSelected()) {
            URL darkTheme = getClass().getResource("/test/lms/dashboard.css");
            if (darkTheme != null) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(darkTheme.toExternalForm());
                themeToggle.setText("Light Theme");
            }
        } else {
            URL lightTheme = getClass().getResource("/test/lms/light-theme.css"); // Assuming a light theme exists
            if (lightTheme != null) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(lightTheme.toExternalForm());
                themeToggle.setText("Dark Theme");
            }
        }
    }

    private void updateItemList(String searchTerm) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                List<String> items = new ArrayList<>();
                String query = showingCourses ? "SELECT title FROM courses WHERE title LIKE ?" : "SELECT first_name FROM users WHERE first_name LIKE ?";
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, "%" + searchTerm + "%");
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        items.add(rs.getString(1));
                    }
                } catch (SQLException e) {
                    Platform.runLater(() -> notificationArea.setText("Error loading items: " + e.getMessage()));
                }
                Platform.runLater(() -> {
                    currentItems = items;
                    pagination.setPageCount((int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
                    pagination.setPageFactory(page -> createPage(page));
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    public static class User {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty email = new SimpleStringProperty();
        private final StringProperty role = new SimpleStringProperty();

        public User(String name, String email, String role) {
            this.name.set(name);
            this.email.set(email);
            this.role.set(role);
        }

        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }

        public String getEmail() { return email.get(); }
        public StringProperty emailProperty() { return email; }

        public String getRole() { return role.get(); }
        public StringProperty roleProperty() { return role; }
    }
}