module test.lms {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    // Add Spring Security dependencies
    requires spring.security.core;
    requires spring.security.crypto; // This line is crucial for BCryptPasswordEncoder

    opens test.lms to javafx.fxml; // Allow JavaFX to access your package
    exports test.lms; // Export your package
}