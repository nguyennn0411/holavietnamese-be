# SQL Server in Docker

Local development uses SQL Server 2022 Developer in a dedicated Docker Compose project.
The container listens at `127.0.0.1:14330`; port 1433 was already occupied on this machine.
SQL Server data persists in the Docker volume `hola-vietnamese_sqlserver-data`.
The existing host SQL Server and MySQL configuration are separate.

## Start

Run from `D:\DO_AN\BE` with Docker Desktop running in Linux-container mode:

```powershell
.\start-sqlserver.ps1
```

This starts SQL Server, waits for its health check, and creates `hola_vietnamese` if absent.
The local `.env.sqlserver` file contains a generated password and is ignored by Git.
On another machine, copy `.env.sqlserver.example` to `.env.sqlserver` and replace its example password first.

To start the backend with the SQL Server profile:

```powershell
.\start-sqlserver.ps1 -StartBackend
```

Maven is not on PATH on this machine, so use:

```powershell
.\start-sqlserver.ps1 -StartBackend -MavenCommand 'C:\apache-maven-3.9.12\bin\mvn.cmd'
```

The helper builds and runs the executable JAR, loads the database password without printing it,
and restores its process environment when the backend exits.
Flyway creates the application tables on backend startup, then Hibernate validates the schema.
The default backend profile is `sqlserver`. It reads `.env.sqlserver` from the working
directory automatically (as a properties file); existing environment variables can override
these local values. Keep this file untracked and do not put passwords in source code.
In IntelliJ, open the `BE` project, use JDK 21 and set the run working directory to
`$PROJECT_DIR$` (the `BE` folder). Start Docker SQL Server once, then run
`BackendApplication` normally; no active profile or password field is required in the IDE.
The `Hola SQL Server` run configuration remains available as a convenience.
This connects to an already-running container; it does not start Docker Desktop or containers.
Use an ASCII-only project path if IntelliJ's build process corrupts Vietnamese path characters.
To use the original MySQL configuration instead, explicitly set `SPRING_PROFILES_ACTIVE=mysql`.
This config does not copy existing MySQL data into SQL Server.

## Connect using SSMS or a database client

| Setting | Value |
| --- | --- |
| Server | `localhost,14330` |
| Authentication | SQL Server Authentication |
| Login | `sa` |
| Password | `MSSQL_SA_PASSWORD` in `.env.sqlserver` |
| Database | `hola_vietnamese` |
| Encryption | Enabled; trust server certificate for this local container |

The JDBC URL is:

```text
jdbc:sqlserver://localhost:14330;databaseName=hola_vietnamese;encrypt=true;trustServerCertificate=true
```

Override `SQLSERVER_URL` and `SQLSERVER_USERNAME` when needed. The password comes from `MSSQL_SA_PASSWORD`.
The self-signed certificate trust setting and SA login are for this loopback-only development setup.
SQL Server Developer is for development/testing; production needs appropriate licensing and credentials.
Changing the password in the env file does not rotate an existing database's SA password.

## Useful commands

```powershell
docker compose --env-file .env.sqlserver -f compose.sqlserver.yml ps -a
docker compose --env-file .env.sqlserver -f compose.sqlserver.yml logs --tail=50 sqlserver
docker compose --env-file .env.sqlserver -f compose.sqlserver.yml stop
docker compose --env-file .env.sqlserver -f compose.sqlserver.yml up -d
```

`stop` and ordinary `down` retain the data volume. Do not add `-v` to `down` unless you intend to erase the database.

## Implementation

- `compose.sqlserver.yml`: official Microsoft image, loopback port, persistent volume, health check, and idempotent database initialization.
- `application-sqlserver.yml`: JDBC driver, SQL Server dialect, Unicode string mapping, UTC timestamp mapping, and separate Flyway location.
- `db/sqlserver/V1__courses_and_enrollments.sql` and `V2__vocabulary_notebook.sql`: SQL Server schema with IDENTITY keys, NVARCHAR, BIT, DATETIME2, foreign keys, and unique constraints.
- MySQL-native progress queries were replaced with JPQL so the same repository adapters work on both databases.
- Large lesson/course text mappings are now portable across the database dialects.
- Integration fixtures support SQL Server identity inserts and refuse to clear any non-test database.
- Empty vocabulary searches are explicitly handled because SQL Server's string-search behavior differs from MySQL.

## Verification

- Docker SQL Server health check passed; the database initializer exited successfully.
- All 13 backend tests passed against the real SQL Server container, including Vietnamese vocabulary CRUD,
  concurrent enrollment, concurrent lesson completion, and session authentication.
- The default H2 tests and executable JAR packaging also passed after the portability changes.
- The PowerShell startup helper was executed successfully.
- The backend started on port 8080 with the sqlserver profile, created all six application tables
  plus Flyway history in hola_vietnamese, and GET /api/courses returned HTTP 200.

## Test database

Integration tests use a separate disposable database named `hola_features_test`, never `hola_vietnamese`.
Create it inside the container:

```powershell
docker compose --env-file .env.sqlserver -f compose.sqlserver.yml run --rm --no-deps sqlserver-init -S sqlserver -U sa -C -b -Q "IF DB_ID(N'hola_features_test') IS NULL CREATE DATABASE [hola_features_test];"
$env:SPRING_PROFILES_ACTIVE = 'sqlserver'
$env:SQLSERVER_URL = 'jdbc:sqlserver://localhost:14330;databaseName=hola_features_test;encrypt=true;trustServerCertificate=true'
$env:MSSQL_SA_PASSWORD = (Get-Content .env.sqlserver | Where-Object { $_ -like 'MSSQL_SA_PASSWORD=*' }).Substring(18)
mvn test
```

Use a dedicated terminal for these test environment variables, or clear them before running the normal application.
Fixtures reset only the named test database. The default `mvn test` without the profile continues to use isolated H2.

References: [Microsoft SQL Server container guide](https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker?view=sql-server-ver16)
and [Flyway SQL Server support](https://documentation.red-gate.com/fd/sql-server-database-277579330.html).
