## Overview

[Jackson](../../../../jackson) (Java) data format module that supports reading and writing
[CBOR](https://www.rfc-editor.org/info/rfc7049)
("Concise Binary Object Representation") encoded data.
Module extends standard Jackson streaming API (`JsonFactory`, `JsonParser`, `JsonGenerator`), and as such works seamlessly with all the higher level data abstractions (data binding, tree model, and pluggable extensions).

It also supports:

* CBOR Sequences ([RFC-8742](https://www.rfc-editor.org/rfc/rfc8742.html)) (root-level value sequences)
    * uses standard Jackson databind `MappingIterator` and `SequenceWriter` abstractions

## Status

This module is considered stable and production quality (since 2.4). Similar to JSON- and other JSON-like
backends, it implements full support for all levels (streaming, data-binding, tree model).

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-cbor/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-cbor/)
[![Javadoc](https://javadoc.io/badge/com.fasterxml.jackson.dataformat/jackson-dataformat-cbor.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-cbor)

### Limitations

Minor limitations exist with respect to advanced type-handling of `CBOR` format:

* While tags are written for some types (`BigDecimal`, `BigInteger`), they are not handled on parsing

# Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-cbor</artifactId>
  <version>2.13.3</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

Basic usage is by using `CBORFactory` in places where you would usually use `JsonFactory`
(or `CBORFactory` instead `JsonMapper`/`ObjectMapper`)

```java
ObjectMapper mapper = new CBORMapper();
// and then read/write data as usual
SomeType value = ...;
byte[] cborData = mapper.writeValueAsBytes(value);
SomeType otherValue = mapper.readValue(cborData, SomeType.class);
```

Implementation allows use of any of 3 main operating modes:

* Streaming API (`CBORParser` and `CBORGenerator`)
* Databinding (via `ObjectMapper` / `ObjectReader` / `ObjectWriter`)
    * Use subtype `CBORMapper`
* Tree Model (using `TreeNode`, or its concrete subtype, `JsonNode` -- not JSON-specific despite the name)

and all the usual data-binding use cases exactly like when using `JSON` or `Smile` (2 canonical 100% supported Jackson data formats).

# Documentation

* [Wiki](../../../wiki) (includes Javadocs)
