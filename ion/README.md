# Overview

[Jackson](/FasterXML/jackson) data format module for reading and writing
[Ion](https://amznlabs.github.io/ion-docs/) encoded data.

Project is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

# Status

Since version 2.8 this module is considered complete and production ready.
All Jackson layers (streaming, databind, tree model) are supported.

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-ion</artifactId>
  <version>2.9.1</version>
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

See [Wiki](../../../wiki) (includes Javadocs)
