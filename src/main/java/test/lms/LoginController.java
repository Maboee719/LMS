package test.lms;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

public class LoginController {
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label loginMessageLabel;

    // Handle login button click
    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            loginMessageLabel.setText("Please enter both email and password");
            return;
        }

        try {
            String role = validateLoginAndGetRole(email, password);
            if (role != null) {
                loginMessageLabel.setText("Login successful!");

                // Load dashboard based on role
                loadDashboardForRole(role);
            } else {
                loginMessageLabel.setText("Invalid email or password");
            }
        } catch (SQLException e) {
            loginMessageLabel.setText("Database error: " + e.getMessage());
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            loginMessageLabel.setText("System error: Password hashing failed");
            e.printStackTrace();
        }
    }

    // Validate user credentials and fetch role
    private String validateLoginAndGetRole(String email, String password) throws SQLException, NoSuchAlgorithmException {
        String url = "jdbc:postgresql://localhost:5432/lms";
        String dbUser = "postgres"; // your DB username
        String dbPassword = "654321"; // your DB password

        String query = "SELECT user_type FROM users WHERE email = ? AND password_hash = ?";

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, email);
            stmt.setString(2, hashPasswordSHA256(password));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("user_type");
                    if (role != null) {
                        role = role.trim().toLowerCase(); // normalize for comparison
                    }
                    return role; // e.g., "administrator", "student", "instructor"
                } else {
                    return null; // no match found
                }
            }
        }
    }

    // Hash password with SHA-256
    private String hashPasswordSHA256(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(password.getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Load dashboard based on user role
    private void loadDashboardForRole(String role) {
        try {
            Parent root;
            Stage stage = (Stage) emailField.getScene().getWindow();

            switch (role) {
                case "administrator":
                case "admin": // handle possible variations
                case "administrator ":
                    root = FXMLLoader.load(getClass().getResource("/test/lms/AdminDashboard.fxml"));
                    break;
                case "student":
                    root = FXMLLoader.load(getClass().getResource("/test/lms/StudentDashboard.fxml"));
                    break;
                case "instructor":
                    root = FXMLLoader.load(getClass().getResource("/test/lms/InstructorDashboard.fxml"));
                    break;
                default:
                    loginMessageLabel.setText("Unknown role: " + role);
                    return;
            }

            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            loginMessageLabel.setText("Error loading dashboard");
        }
    }

    // Show signup scene
    @FXML
    private void showSignupScreen() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/test/lms/signup.fxml"));
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            loginMessageLabel.setText("Error loading signup window");
        }
    }
}