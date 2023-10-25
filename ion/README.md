# Overview

[Jackson](../../../../jackson) data format module for reading and writing
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
  <version>2.11.1</version>
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

### java.time JSR 310

Version 2.12 (released on November 28, 2020) added support for
(de)serializing some `java.time` classes directly from/to Ion timestamp values.
To enable it, you need to register module `IonJavaTimeModule` like so:

```java
IonObjectMapper mapper = IonObjectMapper.builder()
        .addModule(new IonJavaTimeModule())
        //Disable writing dates as numeric timestamp values to allow writing as Ion timestamp values. 
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
```

## Documentation

See [Wiki](../../../wiki) (includes Javadocs)
