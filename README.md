
Learning Management System (LMS) Documentation
Project Description
As part of a group project, I developed a desktop-based Learning Management System (LMS) using Java, with JavaFX for the graphical user interface (GUI) and PostgreSQL for data management via JDBC. The LMS is designed for educational institutions to manage courses, assignments, user roles (admin, instructor, student), and track progress. My implementation incorporates dynamic graphics, animations, and secure database connectivity to enhance user interaction. I hosted the project on my GitHub repository for version control and collaboration, ensuring a modern and interactive academic experience.
The LMS supports role-based functionality:
•	Admins manage users.
•	Instructors create and manage courses and assignments, and grade submissions.
•	Students enroll in courses, submit assignments, and view scores.
The code is structured in a single class, LMSApplication.java, with modular methods for UI creation, database operations, and user interactions. I created a CSS stylesheet (styles.css) to enhance the visual design and implemented logging for debugging and auditing. The project is hosted at https://github.com/Maboee719/LMS/.





Assignment Question Alignment
We designed the LMS to fulfill the requirements specified in the assignment, which include:
•	Technologies:
o	Frontend: JavaFX for a responsive and dynamic GUI.
o	Backend: PostgreSQL for robust data storage.
o	Database Driver: JDBC for secure database connectivity.
o	Version Control: Git and GitHub for collaborative development.
o	Build Tool: Maven (assumed for dependency management, though configurable with Gradle).
•	Key Features:
o	A menu bar with functional items (e.g., File > Exit).
o	A ScrollPane with pagination displaying at least 20 dummy elements (users/courses).
o	Progress indicators (Progress Bar and custom chart) for course/student progress.
o	Visual effects (DropShadow and FadeTransition) on buttons.
o	Secure PostgreSQL integration for data management.
o	Exception handling for runtime and SQL errors.
o	GitHub repository hosting with a functional link.




Code Explanation
We wrote the LMSApplication class, which extends javafx.application.Application and serves as the main entry point. Below, We explain the code’s structure and functionality, organized by key components and their alignment with the assignment requirements.
1. Data Models
I defined four static inner classes to model the data:
•	User: Stores user details (ID, username, email, role: admin/instructor/student).
•	Course: Represents courses with attributes like ID, name, description, instructor ID, and progress.
•	Assignment: Manages assignments with ID, course ID, title, description, max score, and deadline.
•	Submission: Tracks assignment submissions with ID, assignment ID, student ID, content, file name, score, and feedback.
These models ensure structured data handling and seamless integration with the UI and database.
2. Database Integration
•	Setup: In the start() method, We established a PostgreSQL connection using DriverManager.getConnection() with credentials from environment variables or defaults (DB_URL, DB_USER, DB_PASSWORD).
•	Table Creation: The createTables() method initializes tables for users, courses, enrollments, assignments, submissions, and audit logs. We included dummy data (3 users, 2 courses, 1 enrollment, 1 assignment) to populate empty tables for testing.
•	Operations:
o	Authentication/Registration: The authenticateUser() method verifies credentials using BCrypt password hashing, and registerUser() adds new users with email validation via isValidEmail().
o	CRUD Operations: Methods like addUser(), addCourse(), addAssignment(), enrollInCourse(), submitAssignment(), and gradeSubmissions() handle create, read, update, and delete operations using prepared statements to prevent SQL injection.
o	Connection Management: The stop() method closes the database connection to free resources.
•	Security: We used environment variables for credentials and hashed passwords with BCrypt for secure storage.
3. User Interface
I designed the UI to be intuitive and role-specific:
•	Login Screen (showLoginScreen()): A GridPane with username and password fields, login and register buttons, and a theme toggle (light/dark mode). I applied DropShadow effects to buttons for a modern look.
•	Register Screen (showRegisterScreen()): Similar to the login screen, with additional fields for email and role selection.
•	Main Screen (showMainScreen()): A BorderPane with:
o	Top: A menu bar (createMenuBar()) with File, Help, and Account menus.
o	Center: A VBox containing a dashboard (createDashboard()) with a progress chart and a ScrollPane with pagination (createPage()).
o	Bottom: Role-based action buttons (createActionButtons()) with DropShadow and FadeTransition effects.
•	Role-Specific Screens:
o	Student: View course content (viewCourseContent()), submit assignments (submitAssignment()), and view scores (viewScores()).
o	Instructor: Manage course content (manageCourseContent()) and grade submissions (gradeSubmissions(), showFeedbackPanel()).
o	Admin: Add users (addUser()).
4. Visual Effects
I implemented two visual effects to enhance the UI:
•	DropShadow: Applied to buttons (e.g., login, register, action buttons) using setEffect(new DropShadow(6, Color.GRAY)) or setEffect(new DropShadow(10, Color.GRAY)) for a raised, 3D effect.
•	FadeTransition: Added to the "Check Notifications" button in createActionButtons(). The transition cycles the button’s opacity between 1.0 and 0.3 every 2 seconds to draw user attention.
5. Progress Indicators
I included two types of progress indicators:
•	ProgressBar: Displayed in createPage() for each course, showing progress (0.0 to 1.0) retrieved from the enrollments table.
•	Custom Chart: The drawProgressChart() method uses a Canvas to draw a bar chart of up to five enrollment progress values, styled to match the light/dark theme.
6. Pagination and ScrollPane
•	Pagination: The createPage() method generates paginated views of items (users for admins, courses for instructors/students) with 5 items per page (ITEMS_PER_PAGE). The Pagination control dynamically calculates the number of pages based on the item count.
•	ScrollPane: Wraps the pagination control in showMainScreen(), enabling smooth scrolling for large datasets. The loadItems() method populates at least 20 dummy elements (users or courses) via database queries.
7. Exception Handling
I implemented comprehensive exception handling:
•	SQL Exceptions: Caught in all database operations (e.g., createTables(), loadItems(), addCourse()) and logged using Logger. User-friendly alerts are displayed via showAlert().
•	Input Validation: Methods like registerUser(), addAssignment(), and showFeedbackPanel() validate inputs (e.g., email format, numeric values) and show alerts for errors.
•	File Handling: The submitAssignment() method gracefully handles null file selections.
•	Logging: The setupLogging() method configures a FileHandler to log errors and actions to lms.log.
8. GitHub Integration
We hosted the project on my GitHub repository: https://github.com/Maboee719/LMS/.
 The repository includes:
•	Source code (LMSApplication.java, styles.css).
•	A pom.xml for Maven dependency management (if used).
•	A README.md with setup and usage instructions.
•	Commit history reflecting contributions from all group members, ensuring collaborative development.

Conclusion
I successfully developed an LMS that meets all assignment requirements, delivering a robust, user-friendly platform for educational institutions. My implementation leverages JavaFX for a dynamic GUI, PostgreSQL for secure data management, and GitHub for collaboration, showcasing my skills in Java development, database integration, and version control.

