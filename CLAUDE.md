# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Behavioral

1. Don’t assume. Don’t hide confusion. Surface tradeoffs.
2. Minimum code that solves the problem. Limit speculative additions.
3. Touch only what you must, clean up only your own mess -- but do suggest additional related fixes.
4. Define success criteria. Loop until verified.

## Project Overview

Jackson Dataformats Binary is a multi-module umbrella project containing Jackson binary
format backends: CBOR, Smile, Avro, Protobuf, and Ion. Each module provides Jackson
streaming (`TokenStreamFactory` / `JsonParser` / `JsonGenerator`) implementations for one
binary format, plus an `ObjectMapper` subtype.

This file documents work on the **3.x line**: package prefix `tools.jackson.dataformat.*`,
group id `tools.jackson.dataformat`, Java 17 baseline. The 2.x line lives on its own
branches, uses `com.fasterxml.jackson.dataformat.*`, and has different conventions —
notably `<Format>Parser.Feature` / `<Format>Generator.Feature` instead of the standalone
feature enums described below.

## Branches: read this before creating any branch or PR

Active branches, newest-first, with the version each carries:

| Branch | Role |
|---|---|
| `2.21` | Oldest maintained 2.x patch line; most bug fixes start here |
| `2.22` | Current 2.x patch line |
| `2.x`  | Next 2.x minor; new 2.x features and API additions |
| `3.1`  | Oldest maintained 3.x patch line; where `2.x` lands on the Jackson 3 side |
| `3.2`  | Current 3.x patch line |
| `3.x`  | Jackson 3 development tip; new features and API additions |

For the version a branch carries, read it off `origin`, not a local ref, which may be
stale: `git show origin/<branch>:pom.xml | head -12`.

Two branches look active but are not:

- **`3.0`** is closed. `2.x` merges into `3.1` now, not `3.0`.
- **`master`** is abandoned despite `origin/HEAD` pointing at it: last touched April 2025,
  still carrying `3.0.0-rc3-SNAPSHOT`. **Never target it.**

### Merge-forward model

Changes flow forward and are never cherry-picked backward:

```
2.21  →  2.22  →  2.x  →  3.1  →  3.2  →  3.x
```

Note that `2.x` merges into `3.1`, the oldest maintained 3.x branch, not directly into `3.x`.

Pick the target branch by what the change is:

- **Bug fix present in 2.x too**: the oldest maintained branch that has the bug — usually
  `2.21`. It gets merged forward from there, and reaches 3.x automatically. Fixing it
  directly on a 3.x branch means the 2.x releases never get it, and the next merge-forward
  may conflict.
- **Bug fix in 3-only code**: the oldest maintained 3.x branch that has the bug — usually `3.1`.
- **New feature, new API, new config flag**: `3.x` (and `2.x` if it should also ship on the
  2 line). Patch branches take fixes only.

If unsure whether something counts as a fix or a feature, ask rather than guessing —
targeting too new a branch means the fix never reaches released versions, and targeting
too old a branch means an unwanted API change ships in a patch release.

### PR branch naming

Recent convention, matching what's on the remote:

```
tatu-claude/<target-branch>/<issue-number>-<short-slug>
```

Examples: `tatu-claude/3.1/669-cbor-string-ref`, `tatu-claude/3.1/700-cbor-mapper-spi`,
`tatu-claude/3.2/22-smile-root-context-index`.

The branch segment must match the branch the PR targets.

## Build and Test Commands

Always use the wrapper (`./mvnw` from root, `../mvnw` from a module dir). Requires JDK 17+.

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
├── src/main/java/
│   ├── module-info.java              # JPMS descriptor: exports + provides
│   └── tools/jackson/dataformat/<format>/
│       ├── <Format>Factory.java          # creates parsers/generators
│       ├── <Format>FactoryBuilder.java   # builder for factory construction
│       ├── <Format>Parser.java           # streaming reader
│       ├── <Format>Generator.java        # streaming writer
│       ├── <Format>Mapper.java           # ObjectMapper subclass; Ion differs, see below
│       ├── <Format>ReadFeature.java      # format-specific parser features
│       ├── <Format>WriteFeature.java     # format-specific generator features
│       └── PackageVersion.java.in        # template; PackageVersion.java is generated at build time
├── src/main/resources/META-INF/services/
│   ├── tools.jackson.core.TokenStreamFactory
│   └── tools.jackson.databind.ObjectMapper
├── src/test/java/...
└── pom.xml
```

Registration is declared **twice** — once in `module-info.java` (`provides ... with ...`)
and once under `META-INF/services/` for the classpath. Both must name the same classes; a
mismatch is invisible until runtime (this was issue #700).

There is no `databind/` subpackage on 3.x — databind integration lives in the main package
or in format-specific subpackages (`avro/schema/`, `protobuf/schema/`, `ion/ionvalue/`).

## Architecture

All backends extend the same Jackson 3 core abstractions:

1. **Factory**: `<Format>Factory extends BinaryTSFactory` (a `TokenStreamFactory` subtype —
   there is no `JsonFactory` base class here as there is on 2.x). Configured via
   `<Format>Factory.builder()`.
2. **Parser / Generator**: the streaming layer. `<Format>Parser` decodes bytes to Jackson
   tokens; `<Format>Generator` encodes tokens to bytes. Both are byte-oriented —
   character I/O (`Reader`/`Writer`) is not supported for binary formats.
3. **Mapper**: an `ObjectMapper` subclass with format-specific helpers
   (`CBORMapper.builder()`, `AvroMapper.schemaFor()`). Named `<Format>Mapper` for CBOR,
   Smile, Avro and Protobuf — but Ion breaks the pattern with `IonObjectMapper` and
   `IonValueMapper`. Check the actual class name before referencing it.

Jackson 3 throws unchecked `JacksonException`, not `IOException`: streaming methods are
declared `throws JacksonException`. Don't add `IOException` to signatures, and don't wrap
calls in try/catch just to satisfy the compiler.

### Format-specific notes

- **Avro**: schema is mandatory. Schema generation via `AvroSchemaGenerator`; schema
  handling in `schema/`. Schema evolution uses
  `writerSchema.withReaderSchema(readerSchema)`. `AvroJavaTimeModule` adds `java.time`
  support.
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
| Smile | `BaseTestForSmile` (async tests: `AsyncTestBase`) |
| Avro | `AvroTestBase` (interop tests: `InteropTestBase`) |
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
`@JacksonTestFailureExpected` (from `<module>/.../testutil/failure/`). The annotation
inverts the result: the test fails the build if it unexpectedly *passes*.

When fixing such a bug, move the test out of `tofix/` and drop the annotation.

## Release Notes

Every user-visible change needs an entry, keyed by GitHub issue number. On 3.x the files
are the **unsuffixed** ones — `VERSION-2.x` and `CREDITS-2.x` are the frozen 2.x history
and should not be edited here:

- `release-notes/VERSION` — one entry under the target release, e.g.
  `#693: (avro) Incomplete number length validation in Avro decoder (for BigDecimal)`
- `release-notes/CREDITS` — credit the reporter/contributor, with the fix version
  in parentheses on the following line.

Prefix the description with the module in parens — `(avro)`, `(cbor)`, `(protobuf)`,
`(smile)`, `(ion)`.

## Common Workflows

### Working with schemas (Avro / Protobuf)

```java
mapper.reader(Type.class).with(schema)   // reading
mapper.writer(schema)                    // writing
```

Avro schemas can be generated from POJOs with `AvroSchemaGenerator` or `AvroMapper.schemaFor()`.

### Adding a format-specific feature

New features target `3.x`, not a patch branch.

1. Add the constant to `<Format>ReadFeature` or `<Format>WriteFeature` (these are top-level
   enums implementing `FormatFeature`; on 2.x the same things are nested
   `<Format>Parser.Feature` / `<Format>Generator.Feature`).
2. Set its default state via the constructor arg picked up by `collectDefaults()`.
3. Implement the behavior in the parser/generator.
4. The factory builder's `enable`/`disable`/`configure` overloads pick it up from the enum
   type, so no new builder method is usually needed.
5. Add tests, and a release-notes entry.

This applies to CBOR, Smile, Avro and Ion. **Protobuf has no feature enums at all** —
there is no `ProtobufReadFeature` or `ProtobufWriteFeature`. Adding a Protobuf feature flag
means introducing that machinery first, so treat it as a larger change than the steps above
suggest.

### Adding a new package

`module-info.java` must `export` it, or it is invisible to consumers on the module path
while still working on the classpath — a discrepancy tests usually won't catch.

## Dependencies and Versioning

- Parent POM: `tools.jackson:jackson-base`, pinned to the same version as this project.
- Core dependency: `tools.jackson.core:jackson-core`, same version line.
- CI builds and tests on JDK **17, 21, and 25**; the release build runs on JDK 17.
- Test heap is capped at `-Xmx1024m` (`<argLine>` in the root pom) to surface allocation
  problems — don't raise it to make a test pass.

## Notes

- All formats support Jackson's three API levels: streaming, databind, and tree model.
- `PackageVersion.java` is generated from `PackageVersion.java.in` during the build; edit
  the template, never the generated file.
- `docs/` holds generated javadocs — don't hand-edit it.
