package test.lms;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class SignupController implements Initializable {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> userTypeComboBox;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button loginButton;

    private Connection connection;
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupAnimations();
        setupDatabaseConnection();
        populateUserTypes();
        progressIndicator.setVisible(false);
    }

    private void setupAnimations() {
        FadeTransition fadeTransition = new FadeTransition(Duration.seconds(2), registerButton);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.7);
        fadeTransition.setCycleCount(FadeTransition.INDEFINITE);
        fadeTransition.setAutoReverse(true);
        fadeTransition.play();

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#3299a8"));
        shadow.setRadius(10);

        registerButton.setOnMouseEntered(e -> registerButton.setEffect(shadow));
        registerButton.setOnMouseExited(e -> registerButton.setEffect(null));

        loginButton.setOnMouseEntered(e -> loginButton.setEffect(shadow));
        loginButton.setOnMouseExited(e -> loginButton.setEffect(null));
    }

    private void setupDatabaseConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/lms",
                    "postgres",
                    "654321" // Change this to your actual password
            );
        } catch (Exception e) {
            showError("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void populateUserTypes() {
        userTypeComboBox.getItems().addAll("Student", "Instructor", "Administrator");
        userTypeComboBox.getSelectionModel().selectFirst();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    private void showSuccess(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    @FXML
    private void handleRegister() {
        if (!validateInput()) return;

        progressIndicator.setVisible(true);
        registerButton.setDisable(true);

        new Thread(() -> {
            try {
                if (isEmailTaken(emailField.getText())) {
                    Platform.runLater(() -> {
                        showError("Email already registered.");
                        progressIndicator.setVisible(false);
                        registerButton.setDisable(false);
                    });
                    return;
                }

                registerUser(
                        firstNameField.getText(),
                        lastNameField.getText(),
                        emailField.getText(),
                        hashPassword(passwordField.getText()),
                        userTypeComboBox.getValue()
                );

                Platform.runLater(() -> {
                    showSuccess("Registration successful!");
                    clearForm();
                    progressIndicator.setVisible(false);
                });

            } catch (SQLException e) {
                Platform.runLater(() -> {
                    showError("Registration failed: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    registerButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }

    private boolean validateInput() {
        if (firstNameField.getText().isEmpty() || lastNameField.getText().isEmpty()) {
            showError("Please enter your full name.");
            return false;
        }

        if (!EMAIL_PATTERN.matcher(emailField.getText()).matches()) {
            showError("Enter a valid email.");
            return false;
        }

        if (passwordField.getText().length() < 8) {
            showError("Password must be at least 8 characters.");
            return false;
        }

        if (!passwordField.getText().equals(confirmPasswordField.getText())) {
            showError("Passwords do not match.");
            return false;
        }

        return true;
    }

    private boolean isEmailTaken(String email) throws SQLException {
        String query = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    private void registerUser(String firstName, String lastName, String email, String passwordHash, String userType) throws SQLException {
        String sql = "INSERT INTO users (first_name, last_name, email, password_hash, user_type, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setString(3, email);
            stmt.setString(4, passwordHash);
            stmt.setString(5, userType);
            stmt.executeUpdate();
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing error", e);
        }
    }

    private void clearForm() {
        firstNameField.clear();
        lastNameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        userTypeComboBox.getSelectionModel().selectFirst();
        registerButton.setDisable(false);
    }

    @FXML
    private void handleLoginRedirect() {
        System.out.println("Redirect to login page...");
        // Add your navigation logic here
    }
}