package test.lms;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.geometry.*;
import javafx.scene.effect.DropShadow;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import org.springframework.security.crypto.bcrypt.BCrypt;
import java.io.File;

public class LMSApplication extends Application {
    private static final String DB_URL = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:postgresql://localhost:5432/lms_db";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "postgres";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "654321";
    private static final Logger LOGGER = Logger.getLogger(LMSApplication.class.getName());
    private Connection connection;
    private List<Object> items = new ArrayList<>();
    private static final int ITEMS_PER_PAGE = 5;
    private Stage primaryStage;
    private Scene mainScene;
    private String loggedInUser;
    private String userRole;
    private int loggedInUserId;
    private boolean darkMode = false;

    // Data Models
    public static class User {
        private int id;
        private String username;
        private String email;
        private String role;

        public User(int id, String username, String email, String role) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
        }
        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
    }

    public static class Course {
        private int id;
        private String name;
        private String description;
        private int instructorId;
        private double progress;

        public Course(int id, String name, String description, int instructorId, double progress) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.instructorId = instructorId;
            this.progress = progress;
        }
        public int getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public double getProgress() { return progress; }
    }

    public static class Assignment {
        private int id;
        private int courseId;
        private String title;
        private String description;
        private double maxScore;
        private Timestamp deadline;

        public Assignment(int id, int courseId, String title, String description, double maxScore, Timestamp deadline) {
            this.id = id;
            this.courseId = courseId;
            this.title = title;
            this.description = description;
            this.maxScore = maxScore;
            this.deadline = deadline;
        }
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public double getMaxScore() { return maxScore; }
        public Timestamp getDeadline() { return deadline; }
    }

    public static class Submission {
        private int id;
        private int assignmentId;
        private int studentId;
        private String content;
        private String fileName;
        private Double score;
        private String feedback;

        public Submission(int id, int assignmentId, int studentId, String content, String fileName, Double score, String feedback) {
            this.id = id;
            this.assignmentId = assignmentId;
            this.studentId = studentId;
            this.content = content;
            this.score = score;
            this.feedback = feedback;
        }
        public int getId() { return id; }
        public int getAssignmentId() { return assignmentId; }
        public int getStudentId() { return studentId; }
        public Double getScore() { return score; }
        public String getFeedback() { return feedback; }
        public String getContent() { return content; }
        public String getFileName() { return fileName; }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        setupLogging();

        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            createTables();
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to initialize database: " + e.getMessage());
            LOGGER.severe("Database initialization failed: " + e.getMessage());
            return;
        }

        showLoginScreen();
    }

    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("lms.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (Exception e) {
            LOGGER.warning("Failed to setup logging: " + e.getMessage());
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, username VARCHAR(50) UNIQUE NOT NULL, password_hash VARCHAR(60) NOT NULL, email VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'instructor', 'student')))");
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            if (rs.getInt(1) == 0) {
                String[] users = {"admin1,admin1@example.com,adminpass,admin", "instructor1,instructor1@example.com,instpass,instructor", "student1,student1@example.com,studpass,student"};
                for (String user : users) {
                    String[] parts = user.split(",");
                    stmt.executeUpdate(String.format("INSERT INTO users (username, email, password_hash, role) VALUES ('%s', '%s', '%s', '%s')", parts[0], parts[1], BCrypt.hashpw(parts[2], BCrypt.gensalt()), parts[3]));
                }
            }

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS courses (id SERIAL PRIMARY KEY, course_name VARCHAR(100) NOT NULL, description TEXT, instructor_id INTEGER REFERENCES users(id), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, progress DOUBLE PRECISION DEFAULT 0.0)");
            rs = stmt.executeQuery("SELECT COUNT(*) FROM courses");
            rs.next();
            if (rs.getInt(1) == 0) {
                String[] courses = {"Java Basics,Intro to Java,instructor1,0.5", "Web Dev,HTML/CSS basics,instructor1,0.3"};
                for (String course : courses) {
                    String[] parts = course.split(",");
                    stmt.executeUpdate(String.format("INSERT INTO courses (course_name, description, instructor_id, progress) VALUES ('%s', '%s', (SELECT id FROM users WHERE username = '%s'), %s)", parts[0], parts[1], parts[2], parts[3]));
                }
            }

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS enrollments (user_id INTEGER REFERENCES users(id), course_id INTEGER REFERENCES courses(id), progress DOUBLE PRECISION NOT NULL, PRIMARY KEY (user_id, course_id))");
            rs = stmt.executeQuery("SELECT COUNT(*) FROM enrollments");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO enrollments (user_id, course_id, progress) VALUES ((SELECT id FROM users WHERE username = 'student1'), 1, 0.4)");
            }

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS assignments (id SERIAL PRIMARY KEY, course_id INTEGER REFERENCES courses(id), title VARCHAR(100) NOT NULL, description TEXT, max_score DOUBLE PRECISION, deadline TIMESTAMP)");
            rs = stmt.executeQuery("SELECT COUNT(*) FROM assignments");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO assignments (course_id, title, description, max_score, deadline) VALUES (1, 'Java Homework', 'Complete Chapter 1', 100, NOW() + INTERVAL '7 days')");
            }

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS submissions (id SERIAL PRIMARY KEY, assignment_id INTEGER REFERENCES assignments(id), student_id INTEGER REFERENCES users(id), content TEXT, file_name VARCHAR(255), score DOUBLE PRECISION, feedback TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS audit_logs (id SERIAL PRIMARY KEY, user_id INTEGER REFERENCES users(id), action VARCHAR(100) NOT NULL, details TEXT, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to create tables: " + e.getMessage());
            LOGGER.severe("Table creation failed: " + e.getMessage());
        }
    }

    private void showLoginScreen() {
        GridPane loginLayout = new GridPane();
        loginLayout.getStyleClass().add("root");
        loginLayout.setAlignment(Pos.CENTER);
        loginLayout.setPadding(new Insets(20));
        loginLayout.setVgap(10);
        loginLayout.setHgap(10);
        updateTheme(loginLayout);

        Label titleLabel = new Label("LMS Login");
        titleLabel.setFont(new Font("Arial", 24));
        titleLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));
        GridPane.setHalignment(titleLabel, HPos.CENTER);

        Label userLabel = new Label("Username:");
        userLabel.setFont(new Font("Arial", 14));
        userLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        usernameField.setPrefWidth(250);

        Label passLabel = new Label("Password:");
        passLabel.setFont(new Font("Arial", 14));
        passLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setPrefWidth(250);

        Button loginButton = new Button("Login");
        loginButton.setEffect(new DropShadow(10, Color.GRAY));
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            if (authenticateUser(username, password)) {
                logAudit("Login", "User " + username + " logged in");
                showMainScreen();
            } else {
                showAlert("Login Failed", "Invalid username or password.");
                logAudit("Failed Login", "Failed login attempt for " + username);
            }
        });

        Button registerButton = new Button("Register");
        registerButton.setEffect(new DropShadow(10, Color.GRAY));
        registerButton.setOnAction(e -> showRegisterScreen());

        Button toggleMode = new Button(darkMode ? "Light Mode" : "Dark Mode");
        toggleMode.setOnAction(e -> {
            darkMode = !darkMode;
            toggleMode.setText(darkMode ? "Light Mode" : "Dark Mode");
            updateTheme(loginLayout);
            Scene loginScene = new Scene(loginLayout, 400, 400);
            applyStylesheet(loginScene);
            primaryStage.setScene(loginScene);
        });

        loginLayout.add(titleLabel, 0, 0, 2, 1);
        loginLayout.add(userLabel, 0, 1);
        loginLayout.add(usernameField, 1, 1);
        loginLayout.add(passLabel, 0, 2);
        loginLayout.add(passwordField, 1, 2);
        loginLayout.add(loginButton, 0, 3, 2, 1);
        loginLayout.add(registerButton, 0, 4, 1, 1);
        loginLayout.add(toggleMode, 1, 4);

        Scene loginScene = new Scene(loginLayout, 400, 400);
        applyStylesheet(loginScene);
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("LMS Login");
        primaryStage.show();
    }

    private void showRegisterScreen() {
        GridPane registerLayout = new GridPane();
        registerLayout.getStyleClass().add("root");
        registerLayout.setAlignment(Pos.CENTER);
        registerLayout.setPadding(new Insets(20));
        registerLayout.setVgap(10);
        registerLayout.setHgap(10);
        updateTheme(registerLayout);

        Label titleLabel = new Label("LMS Register");
        titleLabel.setFont(new Font("Arial", 24));
        titleLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#ff9800"));
        GridPane.setHalignment(titleLabel, HPos.CENTER);

        Label userLabel = new Label("Username:");
        userLabel.setFont(new Font("Arial", 14));
        userLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        usernameField.setPrefWidth(250);

        Label emailLabel = new Label("Email:");
        emailLabel.setFont(new Font("Arial", 14));
        emailLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        TextField emailField = new TextField();
        emailField.setPromptText("Enter email");
        emailField.setPrefWidth(250);

        Label passLabel = new Label("Password:");
        passLabel.setFont(new Font("Arial", 14));
        passLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setPrefWidth(250);

        Label roleLabel = new Label("Role:");
        roleLabel.setFont(new Font("Arial", 14));
        roleLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        ChoiceBox<String> roleChoice = new ChoiceBox<>();
        roleChoice.getItems().addAll("admin", "instructor", "student");
        roleChoice.setValue("student");

        Button registerButton = new Button("Register");
        registerButton.setEffect(new DropShadow(10, Color.GRAY));
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String role = roleChoice.getValue();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showAlert("Input Error", "Please fill in all fields.");
                return;
            }

            if (!isValidEmail(email)) {
                showAlert("Input Error", "Please enter a valid email address.");
                return;
            }

            if (registerUser(username, email, password, role)) {
                showAlert("Success", "Registration successful! Please log in.");
                showLoginScreen();
            }
        });

        Button backButton = new Button("Back to Login");
        backButton.setOnAction(e -> showLoginScreen());

        Button toggleMode = new Button(darkMode ? "Light Mode" : "Dark Mode");
        toggleMode.setOnAction(e -> {
            darkMode = !darkMode;
            toggleMode.setText(darkMode ? "Light Mode" : "Dark Mode");
            updateTheme(registerLayout);
            Scene registerScene = new Scene(registerLayout, 400, 450);
            applyStylesheet(registerScene);
            primaryStage.setScene(registerScene);
        });

        registerLayout.add(titleLabel, 0, 0, 2, 1);
        registerLayout.add(userLabel, 0, 1);
        registerLayout.add(usernameField, 1, 1);
        registerLayout.add(emailLabel, 0, 2);
        registerLayout.add(emailField, 1, 2);
        registerLayout.add(passLabel, 0, 3);
        registerLayout.add(passwordField, 1, 3);
        registerLayout.add(roleLabel, 0, 4);
        registerLayout.add(roleChoice, 1, 4);
        registerLayout.add(registerButton, 0, 5, 2, 1);
        registerLayout.add(backButton, 0, 6);
        registerLayout.add(toggleMode, 1, 6);

        Scene registerScene = new Scene(registerLayout, 400, 450);
        applyStylesheet(registerScene);
        primaryStage.setScene(registerScene);
        primaryStage.setTitle("LMS Register");
    }

    private boolean registerUser(String username, String email, String password, String role) {
        try (PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")) {
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                showAlert("Registration Failed", "Username already exists.");
                return false;
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to check username: " + e.getMessage());
            LOGGER.severe("Username check failed: " + e.getMessage());
            return false;
        }

        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, BCrypt.hashpw(password, BCrypt.gensalt()));
            stmt.setString(4, role);
            stmt.executeUpdate();
            logAudit("Register", "New user registered: " + username + " (" + role + ")");
            return true;
        } catch (SQLException e) {
            showAlert("Database Error", "Registration failed: " + e.getMessage());
            LOGGER.severe("Registration failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    private boolean authenticateUser(String username, String password) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id, password_hash, role FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (BCrypt.checkpw(password, storedHash)) {
                    userRole = rs.getString("role");
                    loggedInUserId = rs.getInt("id");
                    loggedInUser = username;
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            showAlert("Database Error", "Authentication failed: " + e.getMessage());
            LOGGER.severe("Authentication error: " + e.getMessage());
            return false;
        }
    }

    private void showMainScreen() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        updateTheme(root);

        root.setTop(createMenuBar());
        VBox centerLayout = new VBox(10);
        centerLayout.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        updateTheme(scrollPane);

        loadItems();
        Pagination pagination = new Pagination((int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        pagination.setPageFactory(this::createPage);
        scrollPane.setContent(pagination);
        centerLayout.getChildren().addAll(createDashboard(), scrollPane);

        root.setCenter(centerLayout);
        root.setBottom(createActionButtons());

        mainScene = new Scene(root, 1000, 700);
        applyStylesheet(mainScene);
        primaryStage.setScene(mainScene);
        primaryStage.setTitle("LMS - " + userRole);

        Button toggleMode = new Button(darkMode ? "Light Mode" : "Dark Mode");
        toggleMode.setOnAction(e -> {
            darkMode = !darkMode;
            toggleMode.setText(darkMode ? "Light Mode" : "Dark Mode");
            showMainScreen();
        });
        ((VBox) root.getCenter()).getChildren().add(toggleMode);
    }

    private Node createDashboard() {
        VBox dashboard = new VBox(10);
        dashboard.setPadding(new Insets(10));
        updateTheme(dashboard);

        Label title = new Label(userRole + " Dashboard");
        title.setFont(new Font("Arial", 20));
        title.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));

        if (userRole.equals("student")) {
            Label info = new Label("Upcoming Assignments: Check below");
            info.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
            dashboard.getChildren().addAll(title, info);
        } else if (userRole.equals("instructor")) {
            Label info = new Label("Pending Grading: Check submissions");
            info.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
            dashboard.getChildren().addAll(title, info);
        } else if (userRole.equals("admin")) {
            Label info = new Label("Manage Users and Courses");
            info.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
            dashboard.getChildren().addAll(title, info);
        }

        Canvas canvas = new Canvas(300, 200);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawProgressChart(gc);
        dashboard.getChildren().add(canvas);

        return dashboard;
    }

    private void drawProgressChart(GraphicsContext gc) {
        gc.clearRect(0, 0, 300, 200);
        gc.setStroke(darkMode ? Color.WHITE : Color.BLACK);
        gc.strokeRect(50, 50, 200, 100);
        try (PreparedStatement stmt = connection.prepareStatement("SELECT progress FROM enrollments WHERE user_id = ? LIMIT 5")) {
            stmt.setInt(1, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            int x = 60;
            while (rs.next()) {
                double progress = rs.getDouble("progress") * 100;
                gc.strokeLine(x, 150, x, 150 - (int)progress);
                x += 40;
            }
        } catch (SQLException e) {
            LOGGER.severe("Chart data failed: " + e.getMessage());
        }
    }

    private void applyStylesheet(Scene scene) {
        URL stylesheet = getClass().getResource("/test/lms/styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        } else {
            System.err.println("❌ styles.css not found at /test/lms/styles.css");
        }
    }


    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        fileMenu.setStyle("-fx-text-fill: white;");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().add(exitItem);

        Menu helpMenu = new Menu("Help");
        helpMenu.setStyle("-fx-text-fill: white;");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAlert("About", "SmartLearn LMS is a desktop app built with JavaFX and PostgreSQL to simplify learning management. It offers secure logins for admins, instructors, and students with role-based features. Users can manage courses, submit assignments, and track progress through visual dashboards. The system supports real-time updates and reliable data storage. Developed using GitHub, it delivers a modern and interactive academic experience.\n"));
        helpMenu.getItems().add(aboutItem);

        Menu accountMenu = new Menu("Account");
        accountMenu.setStyle("-fx-text-fill: white;");
        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        logoutItem.setOnAction(e -> {
            Alert confirmLogout = new Alert(Alert.AlertType.CONFIRMATION);
            confirmLogout.setTitle("Logout Confirmation");
            confirmLogout.setHeaderText("Are you sure you want to logout?");
            confirmLogout.setContentText("You will be returned to the login screen.");
            confirmLogout.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    logAudit("Logout", "User " + loggedInUser + " logged out");
                    loggedInUser = null;
                    userRole = null;
                    loggedInUserId = 0;
                    showLoginScreen();
                }
            });
        });
        accountMenu.getItems().add(logoutItem);

        menuBar.getMenus().addAll(fileMenu, helpMenu, accountMenu);
        return menuBar;
    }

    private void loadItems() {
        items.clear();
        try {
            if (userRole.equals("admin")) {
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT id, username, email, role FROM users");
                    while (rs.next()) {
                        items.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("role")));
                    }
                }
            } else if (userRole.equals("instructor")) {
                try (PreparedStatement stmt = connection.prepareStatement("SELECT id, course_name, description, progress FROM courses WHERE instructor_id = ?")) {
                    stmt.setInt(1, loggedInUserId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        items.add(new Course(rs.getInt("id"), rs.getString("course_name"), rs.getString("description"), loggedInUserId, rs.getDouble("progress")));
                    }
                }
            } else if (userRole.equals("student")) {
                try (PreparedStatement stmt = connection.prepareStatement("SELECT c.id, c.course_name, c.description, c.instructor_id, e.progress FROM courses c JOIN enrollments e ON c.id = e.course_id WHERE e.user_id = ?")) {
                    stmt.setInt(1, loggedInUserId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        items.add(new Course(rs.getInt("id"), rs.getString("course_name"), rs.getString("description"), rs.getInt("instructor_id"), rs.getDouble("progress")));
                    }
                }
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load items: " + e.getMessage());
            LOGGER.severe("Load items failed: " + e.getMessage());
        }
    }

    private VBox createPage(int pageIndex) {
        VBox pageBox = new VBox(10);
        pageBox.setPadding(new Insets(10));
        updateTheme(pageBox);
        int start = pageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        for (int i = start; i < end; i++) {
            Object item = items.get(i);
            VBox itemBox = new VBox(5);
            updateTheme(itemBox);
            itemBox.setOnMouseEntered(e -> itemBox.setStyle(darkMode ? "-fx-background-color: #4f545c;" : "-fx-background-color: #e8f0fe;"));
            itemBox.setOnMouseExited(e -> updateTheme(itemBox));

            if (item instanceof Course course) {
                itemBox.setUserData(course.getId());
                Label nameLabel = new Label(course.getName());
                nameLabel.setFont(new Font("Arial", 16));
                nameLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));

                Label descLabel = new Label(course.getDescription());
                descLabel.setFont(new Font("Arial", 12));
                descLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);

                ProgressBar progressBar = new ProgressBar(course.getProgress());
                progressBar.setPrefWidth(200);
                progressBar.setStyle("-fx-accent: " + (darkMode ? "#7289da" : "#4CAF50") + ";");

                itemBox.getChildren().addAll(nameLabel, descLabel, progressBar);
                if (userRole.equals("student")) {
                    Button viewContent = new Button("View Content");
                    viewContent.setOnAction(e -> viewCourseContent(course.getId()));
                    itemBox.getChildren().add(viewContent);
                } else if (userRole.equals("instructor")) {
                    Button manageContent = new Button("Manage Content");
                    manageContent.setOnAction(e -> manageCourseContent(course.getId()));
                    itemBox.getChildren().add(manageContent);
                }
            } else if (item instanceof User user) {
                itemBox.setUserData(user.getId());
                Label nameLabel = new Label(user.getUsername());
                nameLabel.setFont(new Font("Arial", 16));
                nameLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));
                itemBox.getChildren().add(nameLabel);
            }
            pageBox.getChildren().add(itemBox);
        }
        return pageBox;
    }

    private HBox createActionButtons() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20));
        updateTheme(buttonBox);

        if (userRole.equals("admin")) {
            Button addUser = new Button("Add User");
            addUser.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white;");
            addUser.setEffect(new DropShadow(10, Color.GRAY));
            addUser.setOnAction(e -> addUser());
            buttonBox.getChildren().add(addUser);
        } else if (userRole.equals("instructor")) {
            Button addCourse = new Button("Add Course");
            addCourse.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white;");
            addCourse.setEffect(new DropShadow(10, Color.GRAY));
            addCourse.setOnAction(e -> addCourse());
            buttonBox.getChildren().add(addCourse);

            Button addAssignment = new Button("Add Assignment");
            addAssignment.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white;");
            addAssignment.setEffect(new DropShadow(6, Color.GRAY));
            addAssignment.setOnAction(e -> addAssignment());
            buttonBox.getChildren().add(addAssignment);
        } else if (userRole.equals("student")) {
            Button enroll = new Button("Enroll in Course");
            enroll.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 5; -fx-cursor: hand;");
            enroll.setEffect(new DropShadow(6, Color.GRAY));
            enroll.setOnAction(e -> enrollInCourse());
            buttonBox.getChildren().add(enroll);

            Button submitAssignment = new Button("Submit Assignment");
            submitAssignment.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 5; -fx-cursor: hand;");
            submitAssignment.setEffect(new DropShadow(6, Color.GRAY));
            submitAssignment.setOnAction(e -> submitAssignment());
            buttonBox.getChildren().add(submitAssignment);

            Button viewScores = new Button("View Scores");
            viewScores.setStyle("-fx-background-color: " + (darkMode ? "#ff9800" : "#ff9800") + "; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 5; -fx-cursor: hand;");
            viewScores.setEffect(new DropShadow(6, Color.GRAY));
            viewScores.setOnAction(e -> viewScores());
            buttonBox.getChildren().add(viewScores);
        }

        Button notify = new Button("Check Notifications");
        FadeTransition fade = new FadeTransition(Duration.millis(2000), notify);
        fade.setFromValue(1.0);
        fade.setToValue(0.3);
        fade.setCycleCount(FadeTransition.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();
        notify.setOnAction(e -> showNotifications());
        buttonBox.getChildren().add(notify);

        return buttonBox;
    }

    private void addUser() {
        if (!userRole.equals("admin")) return;
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Add User");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Add", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField username = new TextField(); username.setPromptText("Username");
        TextField email = new TextField(); email.setPromptText("Email");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        ChoiceBox<String> role = new ChoiceBox<>(); role.getItems().addAll("admin", "instructor", "student"); role.setValue("student");
        grid.addRow(0, new Label("Username:"), username);
        grid.addRow(1, new Label("Email:"), email);
        grid.addRow(2, new Label("Password:"), password);
        grid.addRow(3, new Label("Role:"), role);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn.getButtonData() == ButtonBar.ButtonData.OK_DONE ? new User(0, username.getText(), email.getText(), role.getValue()) : null);
        dialog.showAndWait().ifPresent(user -> {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getEmail());
                stmt.setString(3, BCrypt.hashpw(password.getText(), BCrypt.gensalt()));
                stmt.setString(4, user.getRole());
                stmt.executeUpdate();
                loadItems(); refreshPagination();
                showAlert("Success", "User added!");
            } catch (SQLException e) {
                showAlert("Error", e.getMessage());
                LOGGER.severe("Add user failed: " + e.getMessage());
            }
        });
    }

    private void addCourse() {
        if (!userRole.equals("instructor") && !userRole.equals("admin")) return;
        Dialog<Course> dialog = new Dialog<>();
        dialog.setTitle("Add Course");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Add", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField name = new TextField(); name.setPromptText("Course Name");
        TextArea desc = new TextArea(); desc.setPromptText("Description");
        grid.addRow(0, new Label("Name:"), name);
        grid.addRow(1, new Label("Description:"), desc);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn.getButtonData() == ButtonBar.ButtonData.OK_DONE ? new Course(0, name.getText(), desc.getText(), loggedInUserId, 0.0) : null);
        dialog.showAndWait().ifPresent(course -> {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO courses (course_name, description, instructor_id, progress) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, course.getName());
                stmt.setString(2, course.getDescription());
                stmt.setInt(3, loggedInUserId);
                stmt.setDouble(4, course.getProgress());
                stmt.executeUpdate();
                loadItems(); refreshPagination();
                showAlert("Success", "Course added!");
            } catch (SQLException e) {
                showAlert("Error", e.getMessage());
                LOGGER.severe("Add course failed: " + e.getMessage());
            }
        });
    }

    private void addAssignment() {
        if (!userRole.equals("instructor")) return;
        Dialog<Assignment> dialog = new Dialog<>();
        dialog.setTitle("Add Assignment");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Add", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        ChoiceBox<String> courseChoice = new ChoiceBox<>();
        TextField title = new TextField(); title.setPromptText("Title");
        TextArea desc = new TextArea(); desc.setPromptText("Description");
        TextField maxScore = new TextField(); maxScore.setPromptText("Max Score");
        DatePicker deadline = new DatePicker();

        try (PreparedStatement stmt = connection.prepareStatement("SELECT id, course_name FROM courses WHERE instructor_id = ?")) {
            stmt.setInt(1, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("course_name");
                courseChoice.getItems().add(id + " - " + name);
            }
            if (courseChoice.getItems().isEmpty()) {
                showAlert("Error", "No courses available to assign.");
                return;
            }
            courseChoice.setValue(courseChoice.getItems().get(0));
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load courses: " + e.getMessage());
            LOGGER.severe("Load courses failed: " + e.getMessage());
            return;
        }

        grid.addRow(0, new Label("Course:"), courseChoice);
        grid.addRow(1, new Label("Title:"), title);
        grid.addRow(2, new Label("Description:"), desc);
        grid.addRow(3, new Label("Max Score:"), maxScore);
        grid.addRow(4, new Label("Deadline:"), deadline);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    String selectedCourse = courseChoice.getValue();
                    int courseId = Integer.parseInt(selectedCourse.split(" - ")[0]);
                    return new Assignment(0, courseId, title.getText(), desc.getText(), Double.parseDouble(maxScore.getText()), Timestamp.valueOf(deadline.getValue().atStartOfDay()));
                } catch (NumberFormatException e) {
                    showAlert("Error", "Invalid numeric input");
                    return null;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(assignment -> {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO assignments (course_id, title, description, max_score, deadline) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, assignment.courseId);
                stmt.setString(2, assignment.getTitle());
                stmt.setString(3, assignment.getDescription());
                stmt.setDouble(4, assignment.maxScore);
                stmt.setTimestamp(5, assignment.deadline);
                stmt.executeUpdate();
                showAlert("Success", "Assignment added!");
            } catch (SQLException e) {
                showAlert("Error", e.getMessage());
                LOGGER.severe("Add assignment failed: " + e.getMessage());
            }
        });
    }

    private void enrollInCourse() {
        if (!userRole.equals("student")) return;

        int enrollmentCount = 0;
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE user_id = ?")) {
            stmt.setInt(1, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                enrollmentCount = rs.getInt(1);
            }
            if (enrollmentCount >= 6) {
                showAlert("Enrollment Limit Reached", "You cannot enroll in more than 6 courses.");
                return;
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to check enrollments: " + e.getMessage());
            LOGGER.severe("Check enrollments failed: " + e.getMessage());
            return;
        }

        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Enroll in Course");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Enroll", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        ChoiceBox<String> courseChoice = new ChoiceBox<>();

        try (PreparedStatement stmt = connection.prepareStatement("SELECT id, course_name FROM courses WHERE id NOT IN (SELECT course_id FROM enrollments WHERE user_id = ?)")) {
            stmt.setInt(1, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.isBeforeFirst()) {
                showAlert("Error", "No available courses to enroll in.");
                return;
            }
            while (rs.next()) {
                int courseId = rs.getInt("id");
                String courseName = rs.getString("course_name");
                String displayText = String.format("Course ID: %d - %s", courseId, courseName);
                courseChoice.getItems().add(displayText);
            }
            if (!courseChoice.getItems().isEmpty()) {
                courseChoice.setValue(courseChoice.getItems().get(0));
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load available courses: " + e.getMessage());
            LOGGER.severe("Load available courses failed: " + e.getMessage());
            return;
        }

        grid.addRow(0, new Label("Course:"), courseChoice);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String selectedCourse = courseChoice.getValue();
                String[] parts = selectedCourse.split(" - ");
                String idPart = parts[0].replace("Course ID: ", "");
                return Integer.parseInt(idPart);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(courseIdValue -> {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO enrollments (user_id, course_id, progress) VALUES (?, ?, 0.0)")) {
                stmt.setInt(1, loggedInUserId);
                stmt.setInt(2, courseIdValue);
                stmt.executeUpdate();
                loadItems();
                refreshPagination();
                showAlert("Success", "Enrolled in course!");
            } catch (SQLException e) {
                showAlert("Error", e.getMessage());
                LOGGER.severe("Enroll failed: " + e.getMessage());
            }
        });
    }

    private void submitAssignment() {
        if (!userRole.equals("student")) return;

        Dialog<Submission> dialog = new Dialog<>();
        dialog.setTitle("Submit Assignment");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        ChoiceBox<String> assignmentChoice = new ChoiceBox<>();
        ToggleGroup submissionType = new ToggleGroup();
        RadioButton typeRadio = new RadioButton("Type Submission");
        typeRadio.setToggleGroup(submissionType);
        typeRadio.setSelected(true);
        RadioButton fileRadio = new RadioButton("Attach File");
        fileRadio.setToggleGroup(submissionType);
        TextArea content = new TextArea();
        content.setPromptText("Type your submission here");
        content.setPrefHeight(150);
        content.setDisable(false);
        Button attachFileButton = new Button("Choose File");
        attachFileButton.setDisable(true);
        Label fileNameLabel = new Label("No file selected");
        fileNameLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);

        fileRadio.setOnAction(e -> {
            content.setDisable(true);
            attachFileButton.setDisable(false);
        });
        typeRadio.setOnAction(e -> {
            content.setDisable(false);
            attachFileButton.setDisable(true);
            fileNameLabel.setText("No file selected");
        });

        attachFileButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Assignment File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Files", "*.*"),
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                    new FileChooser.ExtensionFilter("Document Files", "*.doc", "*.docx")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                fileNameLabel.setText(selectedFile.getName());
            }
        });

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT a.id, a.title, c.id AS course_id, c.course_name " +
                        "FROM assignments a " +
                        "JOIN courses c ON a.course_id = c.id " +
                        "WHERE a.course_id IN (SELECT course_id FROM enrollments WHERE user_id = ?) AND a.deadline > NOW() " +
                        "AND NOT EXISTS (SELECT 1 FROM submissions s WHERE s.assignment_id = a.id AND s.student_id = ?)")) {
            stmt.setInt(1, loggedInUserId);
            stmt.setInt(2, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.isBeforeFirst()) {
                showAlert("Error", "No available assignments to submit (either past deadline or already submitted).");
                return;
            }
            while (rs.next()) {
                int assignmentId = rs.getInt("id");
                String assignmentTitle = rs.getString("title");
                int courseId = rs.getInt("course_id");
                String courseName = rs.getString("course_name");
                String displayText = String.format("Course: %d - %s Assignment: %d - %s", courseId, courseName, assignmentId, assignmentTitle);
                assignmentChoice.getItems().add(displayText);
            }
            if (!assignmentChoice.getItems().isEmpty()) {
                assignmentChoice.setValue(assignmentChoice.getItems().get(0));
            }
        } catch (SQLException e) {
            showAlert("Error", e.getMessage());
            LOGGER.severe("Fetch assignments failed: " + e.getMessage());
            return;
        }

        grid.addRow(0, new Label("Assignment:"), assignmentChoice);
        grid.addRow(1, new Label("Submission Type:"), typeRadio, fileRadio);
        grid.addRow(2, new Label("Content:"), content);
        grid.addRow(3, new Label("File:"), attachFileButton, fileNameLabel);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String selectedAssignment = assignmentChoice.getValue();
                String[] parts = selectedAssignment.split(" Assignment: ");
                String assignmentPart = parts[1];
                String[] assignmentDetails = assignmentPart.split(" - ");
                int assignmentId = Integer.parseInt(assignmentDetails[0]);
                String submissionContent = content.getText().trim();
                String attachedFileName = fileRadio.isSelected() ? fileNameLabel.getText().equals("No file selected") ? null : fileNameLabel.getText() : null;

                if (typeRadio.isSelected() && submissionContent.isEmpty()) {
                    showAlert("Input Error", "Please type your submission content.");
                    return null;
                }
                if (fileRadio.isSelected() && attachedFileName == null) {
                    showAlert("Input Error", "Please select a file to attach.");
                    return null;
                }

                return new Submission(0, assignmentId, loggedInUserId, typeRadio.isSelected() ? submissionContent : "", attachedFileName, null, null);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(submission -> {
            try (PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO submissions (assignment_id, student_id, content, file_name) VALUES (?, ?, ?, ?)")) {
                insertStmt.setInt(1, submission.getAssignmentId());
                insertStmt.setInt(2, submission.getStudentId());
                insertStmt.setString(3, submission.getContent());
                insertStmt.setString(4, submission.getFileName());
                insertStmt.executeUpdate();
                logAudit("Submission", "User " + loggedInUser + " submitted assignment " + submission.getAssignmentId());
                showAlert("Success", "Assignment submitted!");
            } catch (SQLException e) {
                showAlert("Error", e.getMessage());
                LOGGER.severe("Submit assignment failed: " + e.getMessage());
            }
        });
    }

    private void viewCourseContent(int courseId) {
        VBox contentLayout = new VBox(10);
        contentLayout.getStyleClass().add("root");
        contentLayout.setPadding(new Insets(20));
        updateTheme(contentLayout);
        try (PreparedStatement stmt = connection.prepareStatement("SELECT title, description FROM assignments WHERE course_id = ?")) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Label title = new Label(rs.getString("title"));
                title.setFont(new Font("Arial", 16));
                title.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));
                Label desc = new Label(rs.getString("description"));
                desc.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
                contentLayout.getChildren().addAll(title, desc);
            }
        } catch (SQLException e) {
            showAlert("Error", e.getMessage());
            LOGGER.severe("Load content failed: " + e.getMessage());
        }
        Button back = new Button("Back");
        back.setStyle("-fx-background-color: " + (darkMode ? "#7289da" : "#4CAF50") + "; -fx-text-fill: white;");
        back.setOnAction(e -> primaryStage.setScene(mainScene));
        contentLayout.getChildren().add(back);
        Scene contentScene = new Scene(contentLayout, 600, 400);
        applyStylesheet(contentScene);
        primaryStage.setScene(contentScene);
    }

    private void manageCourseContent(int courseId) {
        VBox contentLayout = new VBox(10);
        contentLayout.getStyleClass().add("root");
        contentLayout.setPadding(new Insets(20));
        updateTheme(contentLayout);
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id, title, description, max_score, deadline FROM assignments WHERE course_id = ?")) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            TableView<Assignment> table = new TableView<>();
            table.setPrefHeight(300);
            TableColumn<Assignment, String> titleCol = new TableColumn<>("Title");
            titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
            TableColumn<Assignment, String> descCol = new TableColumn<>("Description");
            descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
            TableColumn<Assignment, Double> scoreCol = new TableColumn<>("Max Score");
            scoreCol.setCellValueFactory(new PropertyValueFactory<>("maxScore"));
            TableColumn<Assignment, Timestamp> deadlineCol = new TableColumn<>("Deadline");
            deadlineCol.setCellValueFactory(new PropertyValueFactory<>("deadline"));
            table.getColumns().addAll(titleCol, descCol, scoreCol, deadlineCol);
            while (rs.next()) {
                table.getItems().add(new Assignment(rs.getInt("id"), courseId, rs.getString("title"), rs.getString("description"), rs.getDouble("max_score"), rs.getTimestamp("deadline")));
            }
            contentLayout.getChildren().add(table);

            Button grade = new Button("Grade Submissions");
            grade.setEffect(new DropShadow(6, Color.GRAY));
            grade.setOnAction(e -> gradeSubmissions(courseId));
            contentLayout.getChildren().add(grade);
        } catch (SQLException e) {
            showAlert("Error", e.getMessage());
            LOGGER.severe("Load content failed: " + e.getMessage());
        }
        Button back = new Button("Back");
        back.setOnAction(e -> primaryStage.setScene(mainScene));
        contentLayout.getChildren().add(back);
        Scene contentScene = new Scene(contentLayout, 800, 500);
        applyStylesheet(contentScene);
        primaryStage.setScene(contentScene);
    }

    private void gradeSubmissions(int courseId) {
        VBox gradeLayout = new VBox(10);
        gradeLayout.getStyleClass().add("root");
        gradeLayout.setPadding(new Insets(20));
        updateTheme(gradeLayout);

        Label titleLabel = new Label("Grade Submissions");
        titleLabel.setFont(new Font("Arial", 24));
        titleLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));

        TableView<Submission> table = new TableView<>();
        table.setPrefHeight(400);

        TableColumn<Submission, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(cell -> {
            Submission s = cell.getValue();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT username FROM users WHERE id = ?")) {
                stmt.setInt(1, s.getStudentId());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return new javafx.beans.property.SimpleStringProperty(rs.getString("username"));
                }
            } catch (SQLException e) {
                LOGGER.severe("Fetch username failed: " + e.getMessage());
            }
            return new javafx.beans.property.SimpleStringProperty("(Unknown)");
        });
        TableColumn<Submission, String> assignmentCol = new TableColumn<>("Assignment");
        assignmentCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getId() > 0 ? "Assignment " + cell.getValue().getId() : "N/A"));
        TableColumn<Submission, String> contentCol = new TableColumn<>("Content");
        contentCol.setCellValueFactory(new PropertyValueFactory<>("content"));
        TableColumn<Submission, String> fileNameCol = new TableColumn<>("Attached File");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        TableColumn<Submission, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        TableColumn<Submission, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(column -> new TableCell<Submission, String>() {
            private final Button gradeButton = new Button("Grade");
            { gradeButton.setOnAction(event -> { Submission submission = getTableView().getItems().get(getIndex()); showFeedbackPanel(submission, courseId); }); }
            @Override protected void updateItem(String item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : gradeButton); }
        });

        table.getColumns().addAll(studentCol, assignmentCol, contentCol, fileNameCol, scoreCol, actionCol);
        table.setRowFactory(tv -> {
            TableRow<Submission> row = new TableRow<>();
            row.setStyle("-fx-background-color: " + (darkMode ? "#40444b" : "#f9f9f9") + "; -fx-border-color: " + (darkMode ? "#5d6066" : "#e0e0e0") + "; -fx-border-radius: 5;");
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + (darkMode ? "#4f545c" : "#e8f0fe") + "; -fx-border-color: " + (darkMode ? "#7289da" : "#4CAF50") + ";"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-color: " + (darkMode ? "#40444b" : "#f9f9f9") + "; -fx-border-color: " + (darkMode ? "#5d6066" : "#e0e0e0") + ";"));
            return row;
        });

        gradeLayout.getChildren().addAll(titleLabel, table);

        Button back = new Button("Back");
        back.setEffect(new DropShadow(6, Color.GRAY));
        back.setOnAction(e -> manageCourseContent(courseId));
        gradeLayout.getChildren().add(back);

        Scene gradeScene = new Scene(gradeLayout, 1000, 600);
        applyStylesheet(gradeScene);
        primaryStage.setScene(gradeScene);
    }

    private void showFeedbackPanel(Submission submission, int courseId) {
        VBox feedbackPanel = new VBox(20);
        feedbackPanel.getStyleClass().add("root");
        feedbackPanel.setPadding(new Insets(20));
        updateTheme(feedbackPanel);

        Label panelTitle = new Label("Grade Submission #" + submission.getId());
        panelTitle.setFont(new Font("Arial", 20));
        panelTitle.setTextFill(darkMode ? Color.WHITE : Color.web("#3f51b5"));

        TextArea contentView = new TextArea(submission.getContent());
        contentView.setEditable(false);
        contentView.setPrefHeight(150);

        Label fileNameLabel = new Label("Attached File: " + (submission.getFileName() != null ? submission.getFileName() : "None"));
        fileNameLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);

        Label scoreLabel = new Label("Score:");
        scoreLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        TextField scoreField = new TextField(submission.getScore() != null ? String.valueOf(submission.getScore()) : "");
        scoreField.setPrefWidth(100);

        Label feedbackLabel = new Label("Feedback:");
        feedbackLabel.setTextFill(darkMode ? Color.WHITE : Color.BLACK);
        TextArea feedbackField = new TextArea(submission.getFeedback() != null ? submission.getFeedback() : "");
        feedbackField.setPrefHeight(100);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button submitButton = new Button("Submit Feedback");
        submitButton.setOnAction(e -> {
            try {
                double score = Double.parseDouble(scoreField.getText().trim());
                String feedback = feedbackField.getText().trim();
                try (PreparedStatement updateStmt = connection.prepareStatement("UPDATE submissions SET score = ?, feedback = ? WHERE id = ?")) {
                    updateStmt.setDouble(1, score);
                    updateStmt.setString(2, feedback);
                    updateStmt.setInt(3, submission.getId());
                    updateStmt.executeUpdate();
                    logAudit("Grading", "User " + loggedInUser + " graded submission " + submission.getId());
                    showAlert("Success", "Feedback submitted!");
                    gradeSubmissions(courseId);
                } catch (SQLException ex) {
                    showAlert("Error", ex.getMessage());
                    LOGGER.severe("Update feedback failed: " + ex.getMessage());
                }
            } catch (NumberFormatException ex) {
                showAlert("Error", "Invalid score format");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> gradeSubmissions(courseId));

        buttonBox.getChildren().addAll(cancelButton, submitButton);
        feedbackPanel.getChildren().addAll(panelTitle, contentView, fileNameLabel, scoreLabel, scoreField, feedbackLabel, feedbackField, buttonBox);

        Scene feedbackScene = new Scene(feedbackPanel, 500, 500);
        applyStylesheet(feedbackScene);
        primaryStage.setScene(feedbackScene);
    }

    private void viewScores() {
        if (!userRole.equals("student")) return;

        VBox scoresLayout = new VBox(10);
        scoresLayout.getStyleClass().add("root");
        scoresLayout.setPadding(new Insets(20));
        updateTheme(scoresLayout);

        Label titleLabel = new Label("My Scores and Feedback");
        titleLabel.setFont(new Font("Arial", 24));
        titleLabel.setTextFill(darkMode ? Color.WHITE : Color.web("#ff9800"));

        TableView<Submission> table = new TableView<>();
        table.setPrefHeight(400);
        table.setStyle("-fx-background-color: transparent; -fx-table-cell-border-color: transparent;");

        TableColumn<Submission, String> assignmentCol = new TableColumn<>("Assignment");
        assignmentCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getId() > 0 ? "Assignment " + cell.getValue().getId() : "N/A"));
        TableColumn<Submission, String> contentCol = new TableColumn<>("Content");
        contentCol.setCellValueFactory(new PropertyValueFactory<>("content"));
        TableColumn<Submission, String> fileNameCol = new TableColumn<>("Attached File");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        TableColumn<Submission, Double> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        TableColumn<Submission, String> feedbackCol = new TableColumn<>("Feedback");
        feedbackCol.setCellValueFactory(new PropertyValueFactory<>("feedback"));

        table.getColumns().addAll(assignmentCol, contentCol, fileNameCol, scoreCol, feedbackCol);
        table.setRowFactory(tv -> {
            TableRow<Submission> row = new TableRow<>();
            return row;
        });


        scoresLayout.getChildren().addAll(titleLabel, table);

        Button back = new Button("Back");
        back.setEffect(new DropShadow(6, Color.GRAY));
        back.setOnAction(e -> primaryStage.setScene(mainScene));
        scoresLayout.getChildren().add(back);

        Scene scoresScene = new Scene(scoresLayout, 800, 600);
        applyStylesheet(scoresScene);
        primaryStage.setScene(scoresScene);
    }

    private void refreshPagination() {
        Pagination pagination = new Pagination((int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        pagination.setPageFactory(this::createPage);
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        updateTheme(scrollPane);
        scrollPane.setContent(pagination);
        ((VBox)((BorderPane)mainScene.getRoot()).getCenter()).getChildren().set(1, scrollPane);
    }

    private void showNotifications() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Notifications");
        alert.setHeaderText(null);
        StringBuilder content = new StringBuilder();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT title, deadline FROM assignments WHERE deadline > NOW() AND course_id IN (SELECT course_id FROM enrollments WHERE user_id = ?)")) {
            stmt.setInt(1, loggedInUserId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                content.append("Assignment: ").append(rs.getString("title")).append(" due by ").append(rs.getTimestamp("deadline")).append("\n");
            }
        } catch (SQLException e) {
            LOGGER.severe("Fetch notifications failed: " + e.getMessage());
        }
        alert.setContentText(content.length() > 0 ? content.toString() : "No new notifications");
        alert.showAndWait();
    }

    private void logAudit(String action, String details) {
        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO audit_logs (user_id, action, details) VALUES (?, ?, ?)")) {
            stmt.setInt(1, loggedInUserId);
            stmt.setString(2, action);
            stmt.setString(3, details);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Audit log failed: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to close database connection: " + e.getMessage());
        }
    }

    private void updateTheme(Node node) {
        if (node instanceof Pane) {
            Pane pane = (Pane) node;
            // Add or remove dark-mode class
            if (darkMode) {
                pane.getStyleClass().add("dark-mode");
            } else {
                pane.getStyleClass().remove("dark-mode");
            }
            // Ensure all relevant nodes have appropriate style classes
            for (Node child : pane.getChildren()) {
                if (child instanceof Label) {
                    child.getStyleClass().add("label");
                    if (((Label) child).getText().contains("LMS") || ((Label) child).getText().contains("Dashboard")) {
                        child.getStyleClass().add("title");
                    }
                } else if (child instanceof TextField || child instanceof TextArea || child instanceof PasswordField) {
                    child.getStyleClass().add(child instanceof TextField ? "text-field" : child instanceof PasswordField ? "password-field" : "text-area");
                } else if (child instanceof Button) {
                    child.getStyleClass().add("button");
                    String currentStyle = ((Button) child).getStyle();
                    if (currentStyle.contains("ff9800")) {
                        child.getStyleClass().add("orange");
                    }
                } else if (child instanceof Pane) {
                    updateTheme(child);
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}