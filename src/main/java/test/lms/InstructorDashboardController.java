package test.lms;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class InstructorDashboardController {

    @FXML private Label totalCoursesLabel;
    @FXML private Label totalStudentsLabel; // Optional
    @FXML private ProgressBar overallProgressBar;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    @FXML private TableView<Course> coursesTable;
    @FXML private TableColumn<Course, Integer> colId;
    @FXML private TableColumn<Course, String> colName;

    @FXML private TextField courseNameField;
    @FXML private Pagination pagination;

    @FXML private Button shadowButton;
    @FXML private Button fadingButton;

    private ObservableList<Course> coursesList = FXCollections.observableArrayList();

    // Database connection info
    private final String DB_URL = "jdbc:postgresql://localhost:5432/lms";
    private final String DB_USER = "postgres";
    private final String DB_PASSWORD = "654321";

    private Connection conn;

    @FXML
    public void initialize() {
        // Setup table columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Connect to PostgreSQL DB
        connectDB();

        // Load courses into table
        refreshCourses();

        // Set table row click to populate edit field
        coursesTable.setOnMouseClicked((MouseEvent event) -> {
            Course selected = coursesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                courseNameField.setText(selected.getName());
            }
        });

        // Setup DropShadow effect
        DropShadow dropShadow = new DropShadow();
        shadowButton.setEffect(dropShadow);

        // Setup FadeTransition for fadingButton
        FadeTransition fadeTransition = new FadeTransition(Duration.seconds(2), fadingButton);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.3);
        fadeTransition.setCycleCount(Animation.INDEFINITE);
        fadeTransition.setAutoReverse(true);
        fadeTransition.play();
    }

    private void connectDB() {
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            statusLabel.setText("Connected to DB");
        } catch (SQLException e) {
            statusLabel.setText("DB Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void refreshCourses() {
        if (conn == null) return;
        coursesList.clear();

        String sql = "SELECT id, name FROM courses ORDER BY id";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int totalCourses = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                coursesList.add(new Course(id, name));
                totalCourses++;
            }
            coursesTable.setItems(coursesList);
            totalCoursesLabel.setText(String.valueOf(totalCourses));
            statusLabel.setText("Courses refreshed");
        } catch (SQLException e) {
            statusLabel.setText("Error loading courses: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void handleAddCourse(ActionEvent event) {
        String name = courseNameField.getText().trim();
        if (name.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Course name cannot be empty.");
            return;
        }

        String sql = "INSERT INTO courses (name) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            statusLabel.setText("Course added: " + name);
            courseNameField.clear();
            refreshCourses();
        } catch (SQLException e) {
            statusLabel.setText("Error adding course: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdateCourse(ActionEvent event) {
        Course selected = coursesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a course to update.");
            return;
        }
        String newName = courseNameField.getText().trim();
        if (newName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Course name cannot be empty.");
            return;
        }

        String sql = "UPDATE courses SET name = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setInt(2, selected.getId());
            pstmt.executeUpdate();
            statusLabel.setText("Course updated: " + newName);
            refreshCourses();
        } catch (SQLException e) {
            statusLabel.setText("Error updating course: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDeleteCourse(ActionEvent event) {
        Course selected = coursesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a course to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setContentText("Are you sure you want to delete the course: " + selected.getName() + "?");
        if (confirm.showAndWait().get() == ButtonType.OK) {
            String sql = "DELETE FROM courses WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, selected.getId());
                pstmt.executeUpdate();
                statusLabel.setText("Course deleted: " + selected.getName());
                refreshCourses();
                courseNameField.clear();
            } catch (SQLException e) {
                statusLabel.setText("Error deleting course: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    @FXML
    public void viewProgress() {
        // Logic to display progress information
        statusLabel.setText("View Progress clicked");
    }

    @FXML
    public void handleExit(ActionEvent event) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
        Platform.exit();
    }

    @FXML
    public void ManageStudents() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("test/lms/ManageStudents.fxml"));
            VBox manageStudentsView = loader.load();

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            Scene scene = new Scene(manageStudentsView);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load Manage Students view: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class Course {
        private final int id;
        private final String name;

        public Course(int id, String name) {
            this.id = id;
            this.name = name;
        }
        public int getId() { return id; }
        public String getName() { return name; }
    }
}