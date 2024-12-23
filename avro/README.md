# Overview

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-avro/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-avro/)
[![Javadoc](https://javadoc.io/badge/com.fasterxml.jackson.dataformat/jackson-dataformat-avro.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-avro)

This project contains [Jackson](https://github.com/FasterXML/jackson)
extension component for reading and writing data encoded using
[Apache Avro](http://avro.apache.org/) data format.

Project adds necessary abstractions on top to make things work with other Jackson functionality.
It relies on standard Avro library for Avro Schema handling, and some parts of deserialization/serialization.

# Status

Module is based on Jackson 2.x, and has been tested with simple Avro Schemas.
Both serialization and deserialization work.

## Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-avro</artifactId>
    <version>2.16.0</version>
</dependency>
```

(or whatever the latest stable version is)

# Usage

## Schema Not Optional

Avro is strongly Schema-based, and all use requires an Avro Schema.
Since there is little metadata in encoded in Avro data, it is not possible to know anything about structure of data without Schema.

So the first step is to get an Avro Schema. Currently this means that you need to find JSON-based definitions of an Avro Schema, and use standard Avro library to read it in.
(note: in future we hope to simplify this process a bit).

One way to do this is:

```java
// note: AvroSchema is Jackson type that wraps "native" Avro Schema object:

String SCHEMA_JSON = "{\n"
        +"\"type\": \"record\",\n"
        +"\"name\": \"Employee\",\n"
        +"\"fields\": [\n"
        +" {\"name\": \"name\", \"type\": \"string\"},\n"
        +" {\"name\": \"age\", \"type\": \"int\"},\n"
        +" {\"name\": \"emails\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},\n"
        +" {\"name\": \"boss\", \"type\": [\"Employee\",\"null\"]}\n"
        +"]}";
Schema raw = new Schema.Parser().setValidate(true).parse(SCHEMA_JSON);
AvroSchema schema = new AvroSchema(raw);
```

However, note that there is another much more convenient way. If you are itching to know how,
peek at "Generating Avro Schema from POJO definition" section below; otherwise keep on reading.

## Creating ObjectMapper

(note: although you can use Streaming API -- if you really want -- it is unlikely to be very interesting to use directly)

Usage is as with basic `ObjectMapper`, but usually you will construct subtype `AvroMapper`
like so:

```java
AvroMapper mapper = new AvroMapper();
```

since although it is possible to simply construct regular `ObjectMapper` with `AvroFactory`
there are some additional features enabled by and exposed via `AvroMapper`:

1. Ability to construct `AvroSchema` instances with `schemaFrom()` (read textual Avro schema) and `schemaFor()` (generate schema for given Java classes)
2. Support for some Avro-specific datatypes (specifically, serialization "native" Avro schema `org.apache.avro.Schema`)
3. Ignoral of internal pseudo-properties for Avro-generated value classes (included since 2.7.3)

## Reading Avro data

Assuming you have the `schema`, from above, and a POJO definition like:

```java
public class Employee
{
    public String name;
    public int age;
    public String[] emails;
    public Employee boss;
}
```

you can actually use data-binding like so:

```java
byte[] avroData = ... ; // or find an InputStream
Employee empl = mapper.reader(Employee.class)
   .with(schema)
   .readValue(avroData);
```

## Writing avro data

Writing Avro-encoded data follows similar pattern:

```java
byte[] avroData = mapper.writer(schema)
   .writeValueAsBytes(empl);
```

and that's about it, for now.

## Avro Logical Types

The following is an excerpt from the [Logical Types](https://avro.apache.org/docs/1.11.1/specification/#logical-types) section of
the Avro schema specification:

> A logical type is an Avro primitive or complex type with extra attributes to represent a derived type. The attribute 
> `logicalType` must always be present for a logical type, and is a string with the name of one of the logical types 
> listed later in this section. Other attributes may be defined for particular logical types.

Logical types are supported for a limited set of `java.time` classes and for 'java.util.UUID'. See the table below for more details.

### Mapping to Logical Types

Mapping to Avro type and logical type involves these steps:

1. The serializer for a Java type identifies the Jackson type it will serialize into.
2. The `AvroSchemaGenerator` maps that Jackson type to the corresponding Avro type.
3. `logicalType` value is combination of Java type and Jackson type.

#### Java type to Avro Logical Type mapping

| Java type                  | Jackson type    | Generated Avro schema with logical type                                                           |
|----------------------------|-----------------|---------------------------------------------------------------------------------------------------|
| `java.time.OffsetDateTime` | NumberType.LONG | `{"type": "long", "logicalType": "timestamp-millis"}`                                             |
| `java.time.ZonedDateTime`  | NumberType.LONG | `{"type": "long", "logicalType": "timestamp-millis"}`                                             |
| `java.time.Instant`        | NumberType.LONG | `{"type": "long", "logicalType": "timestamp-millis"}`                                             |
| `java.time.LocalDate`      | NumberType.INT  | `{"type": "int",  "logicalType": "date"}`                                                         |
| `java.time.LocalTime`      | NumberType.INT  | `{"type": "int",  "logicalType": "time-millis"}`                                                  |
| `java.time.LocalDateTime`  | NumberType.LONG | `{"type": "long", "logicalType": "local-timestamp-millis"}`                                       |
| `java.util.UUID`           |                 | `{"type": "fixed", "name": "UUID", "namespace": "java.util", "size": 16, "logicalType" : "uuid"}` |

_Provided Avro logical type generation is enabled._

### Usage

Call `AvroSchemaGenerator.enableLogicalTypes()` method to enable Avro schema with logical type generation. 

```java
// Create and configure Avro mapper. With for example a module or a serializer. 
AvroMapper mapper = AvroMapper.builder()
        .build();

AvroSchemaGenerator gen = new AvroSchemaGenerator();
// Enable logical types
gen.enableLogicalTypes();

mapper.acceptJsonFormatVisitor(RootType.class, gen);
Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();
```

_**Note:** For best performance with `java.time` classes configure `AvroMapper` to use `AvroJavaTimeModule`. More on 
`AvroJavaTimeModule` bellow._ 

## Java Time Support

`AvroJavaTimeModule` is the best companionship to enabled to Avro logical types. It provides serialization and 
deserialization for set of `java.time` classes into a simple numerical value, e.g., `OffsetDateTime` to `long`, 
`LocalTime` to `int`, etc.

| WARNING: Time zone information is lost at serialization. After deserialization, time instant is reconstructed but not the original time zone.|
| --- |

Because data is serialized into simple numerical value (long or int), time zone information is lost at serialization. 
Serialized values represent point in time, independent of a particular time zone or calendar. Upon reading a value back, 
time instant is reconstructed but not the original time zone.

`AvroJavaTimeModule` is to be used either as:
- replacement of Java 8 date/time module (`com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`) or
- to override Java 8 date/time module and for that, module must be registered AFTER Java 8 date/time module (last registration wins).

### Java types supported by AvroJavaTimeModule, and their mapping to Jackson types

| Java type                     | Serialization type
| ----------------------------- | ------------------
| `java.time.OffsetDateTime`    | NumberType.LONG
| `java.time.ZonedDateTime`     | NumberType.LONG
| `java.time.Instant`           | NumberType.LONG
| `java.time.LocalDate`         | NumberType.INT
| `java.time.LocalTime`         | NumberType.INT
| `java.time.LocalDateTime`     | NumberType.LONG

### Usage

```java
AvroMapper mapper = AvroMapper.builder()
    .addModule(new AvroJavaTimeModule())
    .build();
```

### Precision

Avro supports milliseconds and microseconds precision for date and time related logical types. `AvroJavaTimeModule` 
supports millisecond precision only.

## Generating Avro Schema from POJO definition

Ok but wait -- you do not have to START with an Avro Schema. This module can
actually generate schema for you, starting with POJO definition(s)!
Here's how

```java
public class POJO {
  // your typical, Jackson-compatible POJO (with or without annotations)
}

ObjectMapper mapper = new ObjectMapper(new AvroFactory());
AvroSchemaGenerator gen = new AvroSchemaGenerator();
mapper.acceptJsonFormatVisitor(RootType.class, gen);
AvroSchema schemaWrapper = gen.getGeneratedSchema();

org.apache.avro.Schema avroSchema = schemaWrapper.getAvroSchema();
String asJson = avroSchema.toString(true);
```

So: you can generate native Avro Schema object very easily, and use that instead of
hand-crafted variant. Or you can even use this method for outputting schemas to use
in other processing systems; use your POJOs as origin of schemata.

## Ok, so you REALLY want Streaming API

Although use of data-binding is strongly recommended, due to strongly typed nature of Avro,
it is actually quite possible to use Jackson Streaming API.

So you can just use underlying `AvroFactory` and parser it produces, for event-based processing:

```java
AvroFactory factory = new AvroFactory();
JsonParser parser = factory.createParser(avroBytes);
// but note: Schema is NOT optional, regardless:
parser.setSchema(schema);
while (parser.nextToken() != null) {
  // do something!
}
```

and similarly with `JsonGenerator`. And as with other fully-supported formats, you can even
mix-and-match data-binding with streaming (see `JsonParser.readValueAs()`).

# Issues

Currently, following things have not been thoroughly tested and may cause problems:

* More advanced features will probably not work well. This includes:
    * Polymorphic type handling
    * Object identity

especially because Avro itself does not have much direct support for polymorphic types
or object identity.

# Documentation

Nothing much yet -- contributions welcome!
