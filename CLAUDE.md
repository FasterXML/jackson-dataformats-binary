# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jackson Dataformats Binary is a multi-module Maven project providing binary format backends for Jackson. It includes support for CBOR, Smile, Avro, Protobuf, and Ion formats. This is the 3.x branch (Java 17+) - Active development happens on `3.x` branch.

Current version: 3.0.2-SNAPSHOT

## Build Commands

All builds use the Maven Wrapper (`./mvnw`):

```bash
# Build and run all tests
./mvnw verify

# Build without tests
./mvnw -DskipTests install

# Run tests only
./mvnw test

# Build a specific module
./mvnw -pl cbor verify
./mvnw -pl smile verify
./mvnw -pl avro verify
./mvnw -pl protobuf verify
./mvnw -pl ion verify

# Run a specific test class
./mvnw -Dtest=CBORFactoryPropertiesTest test -pl cbor

# Run a specific test method
./mvnw -Dtest=CBORFactoryPropertiesTest#testFactoryDefaults test -pl cbor

# Clean build
./mvnw clean verify

# Code coverage
./mvnw test
# Coverage reports in each module's target/site/jacoco/jacoco.xml
```

Maven flags commonly used:
- `-B` - batch mode (non-interactive)
- `-q` - quiet output
- `-ff` - fail-fast
- `-ntp` - no transfer progress

## Module Structure

The project has 5 independent format modules under the parent POM:

- **cbor/** - CBOR (Concise Binary Object Representation) format support
- **smile/** - Smile format support (Jackson's own binary format)
- **avro/** - Apache Avro format support
- **protobuf/** - Protocol Buffers format support
- **ion/** - Amazon Ion format support

## Architecture Patterns

All modules follow a consistent architecture pattern:

### Core Class Hierarchy

Each module implements three core types that extend Jackson abstractions:

1. **Factory** (extends `BinaryTSFactory` or `DecorableTSFactory`)
   - Examples: `CBORFactory`, `SmileFactory`, `AvroFactory`
   - Creates Parser and Generator instances
   - Manages format-specific features via bitfield flags
   - Key methods: `_createParser()`, `_createGenerator()`, `getFormatName()`

2. **Parser** (extends `ParserBase`)
   - Examples: `CBORParser`, `SmileParser`, `AvroParser`
   - Implements binary format-specific parsing logic
   - Uses context classes for state management

3. **Generator** (extends `GeneratorBase`)
   - Examples: `CBORGenerator`, `SmileGenerator`, `AvroGenerator`
   - Implements binary encoding with output buffering

### Builder Pattern

Each factory has a companion builder:

- **FactoryBuilder** (extends `DecorableTSFBuilder<Factory, Builder>`)
  - Examples: `CBORFactoryBuilder`, `SmileFactoryBuilder`
  - Provides fluent API for configuration
  - Methods: `enable(Feature)`, `disable(Feature)`, `configure(Feature, boolean)`

### ObjectMapper Subclasses

Each format provides a specialized mapper:

- **Mapper** (extends `ObjectMapper`)
  - Examples: `CBORMapper`, `SmileMapper`, `AvroMapper`
  - Includes nested `Builder` class for fluent construction
  - Static methods: `builder()`, `shared()` (singleton via `SharedWrapper`)

### Feature Management

Format-specific features are defined as enums implementing `FormatFeature`:

- **ReadFeature** - Parser configuration (e.g., `CBORReadFeature`, `SmileReadFeature`)
- **WriteFeature** - Generator configuration (e.g., `CBORWriteFeature`, `SmileWriteFeature`)

Features use bitfield operations for efficient storage and checking.

### State Management

Most modules use context classes for tracking parse/write state:

- **ReadContext** (extends `TokenStreamContext`) - tracks parsing state
- **WriteContext** (extends `TokenStreamContext`) - tracks generation state

Both support context reuse patterns for performance.

### Format-Specific Extensions

- **Avro**: Includes schema generation via `AvroSchemaGenerator`, requires `AvroModule`, has `AvroAnnotationIntrospector`
- **Protobuf**: Schema loading/generation, descriptor handling
- **Ion**: Supports both textual and binary output, delegates to Amazon's IonSystem
- **Smile**: Supports async non-blocking parsing via `NonBlockingByteArrayParser`
- **CBOR**: Supports tags and simple values (`CBORSimpleValue`)

## Test Structure

Tests follow a consistent pattern across modules:

### Base Classes

Each module has a base test class (e.g., `CBORTestBase`, `BaseTestForSmile`):
- Contains shared sample data (JSON test documents)
- Provides factory methods: `cborParser()`, `cborGenerator()`, `cborMapper()`
- Shared assertion helpers
- All tests extend this base class

### Test Organization

Tests are organized by category in subdirectories:

- `parse/` - Parser-specific tests
- `gen/` - Generator-specific tests
- `mapper/` - ObjectMapper integration tests
- `constraints/` - Resource limit tests
- `dos/` - Denial-of-service protection tests
- `fuzz/` - Fuzz testing results
- `seq/` - Sequence reading/writing tests
- `testutil/` - Test utilities
- `tofix/` - Known issues/planned fixes

Tests use JUnit 5 (project migrated from JUnit 4 in January 2025).

## Common Utilities

- **ByteQuadsCanonicalizer** - Shared symbol table for efficient string name handling
- **Bootstrapper Pattern** - Some formats use bootstrapper classes to defer parser instantiation (e.g., `CBORParserBootstrapper`, `SmileParserBootstrapper`)
- **Constants Classes** - Each format has constants (e.g., `CBORConstants`, `SmileConstants`)

## Package Version Generation

Each module uses maven-replacer-plugin to generate `PackageVersion.java` from `PackageVersion.java.in` template during build.

## Java Version

- This branch (3.x): Java 17+
- Uses Jackson 3.x core libraries
- Parent POM: `tools.jackson:jackson-base:3.0.2-SNAPSHOT`

## Dependencies

All modules depend on:
- `com.fasterxml.jackson.core:jackson-annotations`
- `tools.jackson.core:jackson-core`
- `tools.jackson.core:jackson-databind`

Format-specific modules may have additional dependencies (e.g., Avro uses Apache Avro libraries, Ion uses amazon-ion-java).

## Key Implementation Notes

1. **Immutability**: Factories are immutable - `snapshot()` returns `this`, `copy()` creates new instances
2. **Symbol Management**: All formats use `ByteQuadsCanonicalizer` for string symbol tables
3. **Feature Flags**: Bitfield-based feature management with mask operations
4. **Memory Limits**: Tests run with `-Xmx1024m` to catch oversized allocations
5. **Schema Support**: Avro and Protobuf require FormatSchema, others don't (`canUseSchema()` returns false)
