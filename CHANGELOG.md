
# Changelog

All notable changes to Project Context will be documented
in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/).

Versions prior to 1.0.0 represent pre-release development
milestones recorded in the Maven project configuration.

## [Unreleased]

### Added
- File metadata retrieval through the `get_file_metadata` MCP tool.
- Structured file metadata containing canonical file path,
  file size in bytes, and last modification time.
- Cross-platform GitHub Actions CI for Windows, Linux, and macOS.
- Automated Java 21 builds and Maven test execution.
- Maven dependency caching and Surefire test report artifacts.
- Documentation for CI usage and local validation.

### Fixed
- Cross-platform temporary path canonicalization in MCP tool tests.

### Security
- File metadata retrieval enforces existing project boundaries
  and access policy restrictions.

### Testing
- Added file metadata service, tool, and integration tests.
- Verified the complete test suite across all three supported
  operating systems.
- 415 tests per platform with zero failures and errors.

## [0.7.9] - 2026-11-23

### Added
- Centralized filesystem access policy for registered projects.
- Global and per-project exclusion rules.
- Support for four exclusion types:
    - Exact file names.
    - File extensions.
    - Relative file paths.
    - Recursive directory paths.
- External JSON-based policy configuration.
- Policy configuration validation and atomic persistence.
- Read-only policy inspection through the `get_access_policy`
  MCP tool.

### Changed
- Integrated access policy enforcement into:
    - Full-file reading.
    - File range reading.
    - Batch file reading.
    - Batch range reading.
    - File search.
    - Directory listing.
- Active access policies are loaded once at application startup.
- Excluded filesystem entries are filtered from search results
  and directory listings.
- Added case-insensitive exclusion matching and path normalization.

### Fixed
- Preserved descriptive access-denied errors during range reading.
- Improved filesystem error sanitization.

### Security
- Global exclusions cannot be overridden by project-specific rules.
- Preserved project boundary and symbolic link protections.
- Restricted access to explicitly excluded filesystem entries.
- Kept MCP policy inspection strictly read-only.

### Testing
- Added policy matching, configuration, persistence, security,
  and MCP-over-HTTP integration tests.
- Validated access policy behavior through live MCP execution.
- 384 tests, 344 passed, 40 skipped, zero failures and errors.

## [0.7.6] - 2026-11-20

### Added
- Centralized filesystem path resolution through
  `ProjectPathResolver`.
- Dedicated filesystem path violation exception.

### Changed
- Unified project boundary and canonical path validation.
- Integrated the shared path resolver into file reading,
  file search, and directory listing services.
- Reorganized application DTOs and enums.
- Preserved batch reading delegation to existing file services.
- Reduced duplicated filesystem validation logic.

### Security
- Centralized symbolic link and project boundary protections.
- Improved consistency of filesystem access validation.

### Testing
- Added filesystem security and regression tests.

## [0.7.0] - 2026-11-19

### Added
- Project discovery through the `list_projects` MCP tool.
- Sorted registered project labels.
- Bounded line range reading through the `read_file_range`
  MCP tool.
- Batch file reading through the `read_files` MCP tool.
- Batch range reading through the `read_file_ranges` MCP tool.
- Structured batch requests and results.
- Independent success and failure handling for batch entries.
- Validation of batch size and duplicate entries.

### Changed
- Introduced shared readable-file validation.
- Added a maximum range size of 300 lines.
- Added a 256 KiB file size limit for bounded reading.
- Preserved input order in batch results.
- Improved structured MCP error responses.

### Security
- Enforced existing filesystem boundaries across single
  and batch reading operations.
- Introduced bounded reading to prevent excessive resource usage.

### Testing
- Added direct MCP tool contract tests.
- Expanded project registry and discovery test coverage.
- Added range and batch reading unit tests.
- Expanded MCP-over-HTTP integration coverage.
- Verified batch operations through live MCP execution.

## [0.6.0] - 2026-08-19

### Added
- Recursive project file search through the `search` MCP tool.
- Case-insensitive partial file name matching.
- File extension filtering.
- Optional directory scope for targeted searches.
- Absolute file paths in search results.
- Non-recursive directory listing through the `get_current_tree`
  MCP tool.
- Classification of directory entries as files or directories.

### Security
- Enforced registered project boundaries during filesystem search
  and directory listing.
- Added canonical path validation for filesystem traversal.

### Testing
- Added file search and directory listing service tests.
- Expanded MCP-over-HTTP integration coverage.

## [0.5.0] - 2026-08-17

### Added
- Secure file reading through the `read_file` MCP tool.
- File access backed by registered project roots.
- Preservation of original text content and formatting.
- File reading service integration with application startup
  and the MCP server.

### Security
- Enforced registered project root boundaries.
- Introduced real-path validation to prevent symbolic link escapes.
- Validated file existence, readability, and regular file type.

### Testing
- Added file reading service and MCP-over-HTTP integration tests.

## [0.4.0] - 2026-08-17

### Added
- Immutable multi-project registry.
- Project registration using unique labels and canonical
  root directories.
- Atomic registry initialization.
- Registered project root lookup.
- Project registration logging.

### Changed
- Validated project labels and filesystem root paths.
- Introduced canonical root resolution through Java NIO.
- Improved MCP ping tool metadata, annotations,
  and structured output schema.

### Security
- Rejected invalid project registrations.
- Established registered project roots as the foundation
  for filesystem access boundaries.

### Testing
- Added comprehensive project registry contract tests.
- Expanded ping tool contract coverage.

## [0.3.0] - 2026-08-16

### Changed
- Refined application bootstrap and MCP server lifecycle.
- Improved separation between application services
  and infrastructure.
- Prepared application architecture for project-related
  capabilities.
- Completed HTTP transport and MCP integration hardening.

### Testing
- Expanded MCP initialization and tool execution coverage.

## [0.2.5] - 2026-08-16

### Changed
- Hardened MCP server and HTTP transport integration.
- Improved dependency validation and server lifecycle management.
- Resolved MCP server version from Maven project metadata.

### Testing
- Expanded MCP initialization, session handling,
  and ping execution tests.
- Improved HTTP transport integration coverage.
- Added Jetty lifecycle and graceful shutdown tests.

## [0.2.1-SNAPSHOT] - 2026-08-16

### Changed
- Hardened Jetty HTTP server lifecycle.
- Introduced host, port, and handler configuration validation.
- Retained the active server connector.
- Added reporting of the actual bound server port.
- Implemented graceful shutdown with a bounded stop timeout.
- Enabled server shutdown during JVM termination.

### Testing
- Added dynamic port and invalid configuration tests.
- Expanded server lifecycle test coverage.

## [0.2.0-SNAPSHOT] - 2026-08-16

### Added
- Initial end-to-end MCP server implementation.
- Jetty-based HTTP server.
- Streamable HTTP MCP transport.
- Dedicated `/mcp` endpoint.
- Application-level ping service.
- Public `ping` MCP tool.
- MCP server integration with ChatGPT.

### Testing
- Added initial server, transport, and MCP integration tests.
- Verified MCP initialization over HTTP.
- Verified live MCP tool invocation through an OpenAI tunnel.

## [0.1.0-SNAPSHOT] - 2026-08-11

### Added
- Initial Project Context Maven project structure.
- Java 21 build configuration.
- UTF-8 source encoding.

### Changed
- Replaced the initial Maven skeleton version `1.0-SNAPSHOT`
  with `0.1.0-SNAPSHOT`.
