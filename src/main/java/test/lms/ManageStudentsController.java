package test.lms;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty; // Add this import

public class ManageStudentsController {

    @FXML private TableView<Student> studentsTable;
    @FXML private TableColumn<Student, String> colId;
    @FXML private TableColumn<Student, String> colName;
    @FXML private TextField idField;
    @FXML private TextField nameField;

    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private Student selectedStudent;

    // Inner Student class as the data model
    public static class Student {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;

        public Student(String id, String name) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
        }

        public String getId() {
            return id.get();
        }

        public void setId(String id) {
            this.id.set(id);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public SimpleStringProperty idProperty() {
            return id;
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }
    }

    @FXML
    public void initialize() {
        // Bind table columns to Student properties
        colId.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());

        // Set data
        studentsTable.setItems(students);

        // Handle selection change
        studentsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    selectedStudent = newSelection;
                    if (newSelection != null) {
                        idField.setText(newSelection.getId());
                        nameField.setText(newSelection.getName());
                    }
                }
        );
    }

    @FXML
    private void handleAdd() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            showAlert("Please enter both ID and Name");
            return;
        }
        // Check for duplicate ID
        for (Student s : students) {
            if (s.getId().equals(id)) {
                showAlert("Student with this ID already exists");
                return;
            }
        }
        students.add(new Student(id, name));
        clearFields();
    }

    @FXML
    private void handleUpdate() {
        if (selectedStudent == null) {
            showAlert("No student selected");
            return;
        }
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            showAlert("Please enter both ID and Name");
            return;
        }
        selectedStudent.setId(id);
        selectedStudent.setName(name);
        studentsTable.refresh();
        clearFields();
    }

    @FXML
    private void handleDelete() {
        if (selectedStudent == null) {
            showAlert("No student selected");
            return;
        }
        students.remove(selectedStudent);
        clearFields();
    }

    private void clearFields() {
        idField.clear();
        nameField.clear();
        studentsTable.getSelectionModel().clearSelection();
        selectedStudent = null;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}