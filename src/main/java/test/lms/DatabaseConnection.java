package test.lms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final String URL = "jdbc:postgresql://localhost:5432/lms"; // Replace with your database name
    private static final String USER = "postgres"; // Default username for PostgreSQL
    private static final String PASSWORD = "654321"; // Replace with your PostgreSQL password

    public static Connection getConnection() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connection successful!");
            // Test connection by executing a simple query
            Statement stmt = connection.createStatement();
            stmt.execute("SELECT 1");  // Simple query to test connection
            System.out.println("Connection test query executed successfully.");
        } catch (SQLException e) {
            e.printStackTrace(); // Print error if connection fails
        }
        return connection;
    }
}