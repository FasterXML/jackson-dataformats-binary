# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

Jackson Dataformats Binary is a multi-module umbrella project containing Jackson binary
format backends: CBOR, Smile, Avro, Protobuf, and Ion. Each module provides Jackson
`JsonFactory` / `JsonParser` / `JsonGenerator` implementations for one binary format.

This file documents work on the **2.x line**. The 3.x line lives on separate branches
and has different package names (`tools.jackson.*`) and conventions.

## Branches: read this before creating any branch or PR

Active branches, newest-first, with the version each carries:

| Branch | Version | Role |
|---|---|---|
| `2.21` | `2.21.6-SNAPSHOT` | Oldest maintained patch line; most bug fixes start here |
| `2.22` | `2.22.2-SNAPSHOT` | Current patch line |
| `2.x`  | `2.23.0-SNAPSHOT` | Next minor; new features and API additions go here |
| `3.1`  | `3.1.5-SNAPSHOT`  | Where `2.x` lands on the Jackson 3 side |
| `3.x`  | `3.3.0-SNAPSHOT`  | Jackson 3 development tip |

Versions move with every release. Read them off `origin`, not a local ref, which may be
stale: `git show origin/<branch>:pom.xml | head -12`.

**Do not target `master`.** Despite `origin/HEAD` pointing at it, `master` is abandoned:
it was last touched in April 2025 and still carries `3.0.0-rc3-SNAPSHOT`.

### Merge-forward model

Changes flow forward and are never cherry-picked backward:

```
2.21  →  2.22  →  2.x  →  3.1  →  3.2  →  3.x
```

Note that `2.x` merges into `3.1`, not directly into `3.x`; the 3-line then merges
forward among its own patch branches.

Pick the target branch by what the change is:

- **Bug fix**: the oldest branch that has the bug and is still maintained — usually `2.21`.
  It gets merged forward from there.
- **New feature, new API, new config flag**: `2.x`. Patch branches take fixes only.
- **Jackson 3-only change**: the appropriate 3-line branch.

If unsure whether something counts as a fix or a feature, ask rather than guessing —
targeting too new a branch means the fix never reaches released versions, and targeting
too old a branch means an unwanted API change ships in a patch release.

### PR branch naming

Recent convention, matching what's on the remote:

```
tatu-claude/<target-branch>/<issue-number>-<short-slug>
```

Examples: `tatu-claude/2.21/693-avro-big-dec-length-check`,
`tatu-claude/2.18/696-ion-bignum-constraints`.

The branch segment must match the branch the PR targets.

## Build and Test Commands

Always use the wrapper (`./mvnw` from root, `../mvnw` from a module dir).

```bash
# Build all modules
./mvnw clean install

# Build without running tests
./mvnw clean install -DskipTests

# Verify all modules
./mvnw verify
```

Prefer running tests from inside the module directory — a `-Dtest=` filter run from the
root has to match in every module.

```bash
cd cbor && ../mvnw test                                  # whole module
cd cbor && ../mvnw -Dtest=CBORParserTest test            # one class
cd cbor && ../mvnw -Dtest=CBORParserTest#testSimpleArray test   # one method
```

Coverage:

```bash
./mvnw test jacoco:report      # reports land in <module>/target/site/jacoco/
```

## Repository Structure

Each backend (`avro/`, `cbor/`, `ion/`, `protobuf/`, `smile/`) follows the same layout:

```
<format>/
├── src/main/java/com/fasterxml/jackson/dataformat/<format>/
│   ├── <Format>Factory.java         # creates parsers/generators
│   ├── <Format>Parser.java          # streaming reader
│   ├── <Format>Generator.java       # streaming writer
│   ├── <Format>Mapper.java          # ObjectMapper subclass (2.10+); Ion differs, see below
│   ├── <Format>FactoryBuilder.java  # builder for factory construction (2.10+)
│   ├── PackageVersion.java.in       # template; PackageVersion.java is generated at build time
│   └── databind/                    # databind-specific code, where needed
├── src/test/java/...
└── pom.xml
```

Note the package prefix is `com.fasterxml.jackson.dataformat.*` on 2.x (it becomes
`tools.jackson.dataformat.*` on 3.x).

## Architecture

All backends extend the same Jackson core abstractions:

1. **Factory**: `<Format>Factory extends JsonFactory` — creates format-specific parsers
   and generators. Configured via `<Format>Factory.builder()`.
2. **Parser / Generator**: the streaming layer. `<Format>Parser` decodes bytes to Jackson
   tokens; `<Format>Generator` encodes tokens to bytes. Both are byte-oriented —
   character I/O (`Reader`/`Writer`) is not supported for binary formats.
3. **Mapper**: an `ObjectMapper` subclass with format-specific helpers
   (`CBORMapper.builder()`, `AvroMapper.schemaFor()`). Named `<Format>Mapper` for CBOR,
   Smile, Avro and Protobuf — but Ion breaks the pattern with `IonObjectMapper` and
   `IonValueMapper`. Check the actual class name before referencing it.

### Format-specific notes

- **Avro**: schema is mandatory. Schema generation via `AvroSchemaGenerator`; schema
  handling in `schema/`. `AvroJavaTimeModule` adds `java.time` support.
- **Protobuf**: schema required. Schema representation in `schema/`, generation in
  `schemagen/`.
- **CBOR & Smile**: self-describing, no external schema needed. Simplest to work with.
- **Ion**: Amazon's format, self-describing with optional schema. Maintained by @tgregg.

## Testing

### Base classes

These are not uniformly named — check before extending:

| Module | Base class |
|---|---|
| CBOR | `CBORTestBase` |
| Smile | `BaseTestForSmile` |
| Avro | `AvroTestBase` |
| Protobuf | `ProtobufTestBase` |
| Ion | *(none — tests stand alone)* |

Helpers vary by module. CBOR offers `cborFactory()`, `cborMapper()`, `cborParser(byte[])`,
`cborGenerator(ByteArrayOutputStream)`, `cborDoc(String json)`. Smile's equivalents are
`smileFactory()`, `smileMapper()`, and the underscore-prefixed `_smileParser(...)` /
`_smileDoc(...)`. Avro instead exposes `newMapper()`, `parseSchema(...)`, `toAvro(...)`.

### Writing tests

- JUnit 5 (`org.junit.jupiter.api.Test`), with static-imported assertions.
- Extend the module's base class where one exists, and reuse its helpers.

### Known-failing tests

Tests reproducing an unfixed bug go in the module's `tofix/` package and are annotated
`@JacksonTestFailureExpected` (from `<module>/testutil/failure/`). The annotation inverts
the result: the test fails the build if it unexpectedly *passes*. There is no `failing/`
package on current branches — that was the older name.

When fixing such a bug, move the test out of `tofix/` and drop the annotation.

## Release Notes

Every user-visible change needs an entry, keyed by GitHub issue number:

- `release-notes/VERSION-2.x` — one entry under the target release, e.g.
  `#693: (avro) Incomplete number length validation in Avro decoder (for BigDecimal)`
- `release-notes/CREDITS-2.x` — credit the reporter/contributor, with the fix version
  in parentheses on the following line.

Prefix the description with the module in parens — `(avro)`, `(cbor)`, `(protobuf)`,
`(smile)`, `(ion)`.

## Common Workflows

### Working with schemas (Avro / Protobuf)

```java
mapper.reader(Type.class).with(schema)   // reading
mapper.writer(schema)                    // writing
```

Avro schemas can be generated from POJOs with `AvroSchemaGenerator`.

### Adding a format-specific feature

New features target `2.x`, not a patch branch.

1. Add the flag to `<Format>Parser.Feature` or `<Format>Generator.Feature`.
2. Set its default state in `collectDefaults()`.
3. Implement the behavior in the parser/generator.
4. Expose it via the factory builder if needed.
5. Add tests, and a release-notes entry.

This applies to CBOR, Smile, Avro and Ion. **Protobuf has no parser- or generator-level
`Feature` enum at all** — `ProtobufParser` and `ProtobufGenerator` define neither the enum
nor `collectDefaults()`. Adding a Protobuf feature flag means introducing that machinery
first, so treat it as a larger change than the steps above suggest.

## Dependencies and Versioning

- Parent POM: `jackson-base`, pinned to the same version as this project.
- Core dependency: `jackson-core`, same version line.
- CI builds and tests on JDK **8, 17, and 21**; the release build runs on JDK 8.
- Test heap is capped at `-Xmx1024m` (`<argLine>` in the root pom) to surface allocation
  problems — don't raise it to make a test pass.

## Notes

- All formats support Jackson's three API levels: streaming, databind, and tree model.
- `PackageVersion.java` is generated from `PackageVersion.java.in` during the build; edit
  the template, never the generated file.
