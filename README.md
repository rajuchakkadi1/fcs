## About the assignment

You will find the tasks of this assignment on [CODE_ASSIGNMENT](assignment/CODE_ASSIGNMENT.md) file

## About the code base

Some of this code here is based on https://github.com/quarkusio/quarkus-quickstarts

## Getting started

### Prerequisites

- JDK 17 or newer, with `java` available on your `PATH`.
- Docker running for development and tests. Quarkus Dev Services starts a PostgreSQL container automatically.

Maven is not required separately; use the Maven Wrapper included in `java-assignment`.

### Run tests

From the repository root:

**Windows PowerShell**

```powershell
cd java-assignment
$env:TZ = "UTC"
.\mvnw.cmd -B -ntp "-Duser.timezone=UTC" verify
```

**macOS/Linux**

```bash
cd java-assignment
TZ=UTC ./mvnw -B -ntp -Duser.timezone=UTC verify
```

This runs unit and integration tests and checks the JaCoCo instruction-coverage threshold.

### Run in development mode

From `java-assignment`:

```bash
./mvnw quarkus:dev
```

On Windows, run `.\mvnw.cmd quarkus:dev` instead. The application is available at <http://localhost:8080>.

