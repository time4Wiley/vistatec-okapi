# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Prerequisites
- Java 8 or higher (Java 17 recommended)
- Maven 3.3.3 or higher (`brew install maven` on macOS)
- Ant (optional, for distribution builds)

### Building the Project
```bash
# Full build using Maven
mvn clean install

# Build without running tests
mvn clean install -DskipTests

# Build specific module
mvn clean install -pl okapi/core -am

# Build using Ant (for distributions)
cd deployment/maven
ant all
```

### Running Tests
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl okapi/filters/properties

# Run specific test class
mvn test -Dtest=PropertiesFilterTest

# Run specific test method
mvn test -Dtest=PropertiesFilterTest#testBasicExtraction
```

### Common Development Tasks
```bash
# Generate dependency tree
mvn dependency:tree

# Check for dependency updates
mvn versions:display-dependency-updates

# Format code (if configured)
mvn spotless:apply
```

## High-Level Architecture

The Okapi Framework is an **event-driven, pipeline-based localization framework** that processes multilingual content through a series of steps.

### Core Concepts

1. **Event Stream Architecture**: Documents are processed as streams of events (START_DOCUMENT, TEXT_UNIT, END_DOCUMENT, etc.)

2. **Filter-Pipeline-Writer Pattern**:
   - **Filters** extract content from various file formats into a common event stream
   - **Pipeline Steps** process these events (extraction, translation, quality checks, etc.)
   - **Writers** reconstruct documents from the modified event stream

3. **Resource Model**:
   - `TextUnit`: Core container for translatable text
   - `Skeleton`: Preserves non-translatable formatting
   - `Property`: Metadata attached to resources

### Key Interfaces and Their Purposes

- **IFilter** (`net.sf.okapi.common.filters`): Extracts content from specific file formats
- **IPipelineStep** (`net.sf.okapi.common.pipeline`): Processes events in a pipeline
- **IFilterWriter** (`net.sf.okapi.common.filterwriter`): Writes processed content back to files
- **IResource** (`net.sf.okapi.common.resource`): Base for all content resources

### Module Structure

- `okapi/core/`: Core interfaces, common classes, event definitions
- `okapi/filters/`: Format-specific filters (properties, xliff, html, xml, etc.)
- `okapi/steps/`: Reusable pipeline steps (segmentation, leveraging, word count, etc.)
- `okapi/connectors/`: External service integrations (MT engines, TM systems)
- `okapi/libraries/`: Shared utilities and helpers
- `okapi/tm/`: Translation memory implementations
- `applications/`: End-user applications (Rainbow, Tikal, CheckMate, etc.)

### Creating New Components

When implementing new filters:
1. Extend `AbstractFilter` or implement `IFilter`
2. Override `open()`, `hasNext()`, `next()`, and `close()`
3. Generate appropriate events with attached resources
4. Preserve skeleton information for reconstruction

When implementing new steps:
1. Extend `BasePipelineStep` or implement `IPipelineStep`
2. Override `handleEvent()` to process specific event types
3. Pass events to the next step using `Event.DONE`
4. Add parameters class extending `BaseParameters` if needed

### Testing Approach

- Unit tests use JUnit 4
- Filter tests typically involve roundtrip testing (extract → merge → compare)
- Test resources are stored in `src/test/resources/`
- Use `FilterTestDriver` for filter testing
- Use `PipelineDriver` for pipeline testing

### Important Patterns

1. **Skeleton Preservation**: Non-translatable content is stored in skeleton objects to ensure perfect reconstruction
2. **Event Propagation**: Events flow through the pipeline; each step can modify, consume, or generate events
3. **Parameter Classes**: Configuration is handled through parameter classes with getters/setters
4. **Resource Layers**: Additional data can be attached to resources as layers
5. **Annotation System**: Metadata can be attached to any resource using annotations