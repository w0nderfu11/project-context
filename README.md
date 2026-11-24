# Project Context

## Continuous integration

The **Java CI** workflow runs on pull requests targeting `master` and pushes to `master`. To start it manually, open **Actions → Java CI → Run workflow**.

Each run builds the project and runs the full test suite on Ubuntu, Windows, and macOS with Eclipse Temurin JDK 21. The Maven Wrapper downloads Maven 3.9.11. For local validation, set `JAVA_HOME` to a JDK 21 installation and run the command for your platform from the repository root.

| Platform | Command |
| --- | --- |
| Linux or macOS | `sh ./mvnw -B clean verify` |
| Windows PowerShell | `.\mvnw.cmd -B clean verify` |

Open a workflow run under **Actions → Java CI** and select an operating system to inspect its build log. Under **Artifacts**, download the corresponding `surefire-<runner>` archive for individual test results, including failures and skips. Reports are available when Maven reaches the tests. Locally, they are in `target/surefire-reports/`.
