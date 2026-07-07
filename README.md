# Qodana Bank

A fake online bank web application built with Spring Boot.

## Features

- **Customer Dashboard**: View checking and savings balances.
- **Transfers**: Move money between checking and savings accounts.
- **Support Messaging**: Send messages to support and receive replies.
- **Admin Panel**: View all customer messages and reply to them.

## Prerequisites

- **Java 11** or higher.
- **Maven 3.6+** (You can use the provided `./mvnw` wrapper if available, or install Maven).

## How to Run

1.  **Clone the repository** (if applicable).
2.  **Build the application**:
    ```bash
    mvn clean package
    ```
3.  **Run the application**:
    ```bash
    mvn spring-boot:run
    ```
    Alternatively, run the JAR file:
    ```bash
    java -jar target/bank-0.0.1-SNAPSHOT.jar
    ```
4.  **Access the UI**:
    Open your browser and navigate to `http://localhost:8080`

## Test Credentials

The application is seeded with the following users:

| Username | Password | Role     |
| :------- | :------- | :------- |
| `alice`  | `pass1`  | Customer |
| `bob`    | `pass2`  | Customer |
| `admin`  | `admin123` | Admin    |

## Project Structure

- `src/main/java`: Backend logic (Spring Boot Controllers, Services, Models).
- `src/main/resources/static`: Frontend files (HTML/JS/CSS).
- `src/test/java`: Unit tests.
- `pom.xml`: Maven dependencies and build configuration.
