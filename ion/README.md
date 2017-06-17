# Overview

[Jackson](/FasterXML/jackson) data format module for reading and writing
[Ion](https://amznlabs.github.io/ion-docs/) encoded data.

Project is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

# Status

Since version 2.6(.7) this module is considered complete and production ready.
All Jackson layers (streaming, databind, tree model) are supported.

[![Build Status](https://travis-ci.org/FasterXML/jackson-dataformat-ion.svg?branch=master)](https://travis-ci.org/FasterXML/jackson-dataformat-ion)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-ion/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-ion/)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-ion/badge.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-ion)


To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-ion</artifactId>
  <version>2.7.9</version>
</dependency>
```
## Usage

Basic usage is by using `IonObjectMapper` instead of basic `ObjectMapper`;
or, if only using streaming parser/generator, `IonFactory` instead of `JsonFactory`.

```java
ObjectMapper mapper = new IonObjectMapper();
// and then read/write data as usual
SomeType value = ...;
byte[] encoded = mapper.writeValueAsBytes(value);
SomeType otherValue = mapper.readValue(data, SomeType.class);
```

## Documentation

See [Wiki](../../wiki) (includes Javadocs)
