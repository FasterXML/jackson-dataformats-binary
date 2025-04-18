Project: jackson-datatypes-binary
Modules:
  jackson-dataformat-avro
  jackson-dataformat-cbor
  jackson-dataformat-ion (since 2.9)
  jackson-dataformat-protobuf
  jackson-dataformat-smile

Active maintainers:
  * Tyler Gregg (@tgregg): maintainer of jackson-dataformat-ion
  * Tatu Saloranta (@cowtowncoder): author or co-author of all other modules

------------------------------------------------------------------------
=== Releases ===
------------------------------------------------------------------------

#571: Unable to deserialize a pojo with IonStruct
 (reported, fix contributed by Josh C)

2.19.0-rc2 (07-Apr-2025)

#300: (smile) Floats are encoded with sign extension while doubles without
 (reported by Steven F)
#308: (avro) Incorrect serialization for `LogicalType.Decimal` (Java `BigDecimal`)
 (reported by Idan S)
 (fix contributed by Michal F)
#388: (avro) `@JsonEnumDefaultValue` not supported when using AvroMapper
  to generate schema from Java class
 (requested by @Sonic-Rage)
#422: Avro generation failed with enums containing values with special characters
 (reported by @pfr-enedis)
#535: (avro) AvroSchemaGenerator: logicalType(s) never set for non-date classes
 (reported by Cormac R)
 (fix contributed by Michal F)
#536: (avro) Add Logical Type support for `java.util.UUID`
 (contributed by Michal F)
#539: (avro) Upgrade `org.apache.avro:avro` dependency to 1.11.4
#547: (all) JSTEP-10: Unify testing structure/tools
 (contributed by Joo-Hyuk K)
#568: Improve ASCII decoding performance for `CBORParser`
 (contributed by Manuel S)

2.18.4 (not yet released)

#569: (ion) `IonParser` fails to parse some `long` values saying
  they are out of range when they are not
 (reported, fix suggested by @seadbrane)
- (ion) Upgrade `ion-java` to 1.11.10 (from 1.11.9)

2.18.3 (28-Feb-2025)

#541: (cbor, protobuf, smile) `SmileParser.getValueAsString()` FIELD_NAME bug
 (fix contributed by John H)

2.18.2 (27-Nov-2024)

No changes since 2.18.1

2.18.1 (28-Oct-2024)

#518: (cbor) Should not read past end for CBOR string values
 (contributed by Knut W)

2.18.0 (26-Sep-2024)

#167: (avro) Incompatibility with Avro >=1.9.0 (upgrade to Avro 1.11.3)
 (reported by @Sage-Pierce)
 (fix contributed by Rafał H)
#484: (protobuf) Rework synchronization in `ProtobufMapper`
 (contributed by @pjfanning)
#494: (avro) Avro Schema generation: allow mapping Java Enum properties to
  Avro String values
 (requested by Joachim L)
 (contributed by Michal F)
#508: (avro) Ignore `specificData` field on serialization
 (contributed by @pjfanning)
#509: IonValueMapper.builder() not implemented, does not register modules
 (reported by Robert N)

2.17.3 (01-Nov-2024)

#506: (protobuf) Cannot deserialize `UUID` values
 (reported by @uniquonil)
- (ion) Upgrade `ion-java` to 1.11.9 (from 1.11.8)

2.17.2 (05-Jul-2024)

#497: (ion) Failed copy(): `IonValueMapper` does not override copy()
 (reported by @mr-robert)
#501: (ion) Upgrade `ion-java` to 1.11.8 (from 1.11.7)

2.17.1 (04-May-2024)

#487 (ion): Don't close IonParser on EOF to be compatible with `MappingIterator`
  when source is an empty `InputStream`
 (contributed by Yoann V)
#488 (ion): Upgrade `ion-java` to 1.11.7 (from 1.11.2)
#490 (ion) ION deserialization type change from Double to Float in 2.17.0
 (reported by Florian H)

2.17.0 (12-Mar-2024)

#251 (ion) Unable to deserialize Object with unknown `Timestamp` field
 (reported by @mgoertzen)
#316 (cbor) Uncaught exception in
  `com.fasterxml.jackson.dataformat.cbor.CBORParser._finishShortText`
#392: (cbor, smile) Support `StreamReadConstraints.maxDocumentLength`
  validation for CBOR, Smile
#417: (ion) `IonReader` classes contain assert statement which could throw
  unexpected `AssertionError`
 (fix contributed by Arthur C)
#420: (ion) `IndexOutOfBoundsException` thrown by `IonReader` implementations
  are not handled 
 (fix contributed by Arthur C)
#424: (ion) `IonReader` throws `NullPointerException` for unchecked invalid data
 (fix contributed by Arthur C)
#426: (smile) `SmileParser` throws unexpected IOOBE for corrupt content
 (fix contributed by Arthur C)
#428: (ion) `IonParser.getIntValue()` fails or does not handle value overflow checks
 (fix contributed by Thomas d-L)
#432: (ion) More methods from `IonReader` could throw an unexpected `AssertionError`
 (fix contributed by Arthur C)
#434: (ion) Unexpected `NullPointerException` thrown from `IonParser::getNumberType()`
 (fix contributed by Arthur C)
#437: (ion) `IonReader.next()` throws NPEs for some invalid content
#449: (avro) `IndexOutOfBoundsException` in `JacksonAvroParserImpl` for invalid input
 (fix contributed by Arthur C)
#451: (cbor) `IndexOutOfBoundsException` in `CBORParser` for invalid input
 (fix contributed by Arthur C)
#458: (cbor) Unexpected NullPointerException in `CBORParser`
 (fix contributed by Arthur C)
#460: (protobuf) Unexpected `NullPointerException` in `ProtobufParser.currentName()`
 (fix contributed by Arthur C)
#462: (protobuf) `ProtobufParser.currentName()` returns wrong value at root level
#464: (cbor) Unexpected `ArrayIndexOutOfBoundsException` in `CBORParser`
  for corrupt String value
 (fix contributed by Arthur C)
#469 (ion) IonReader.newBytes() throwing `NegativeArraySizeException`
 (contributed by @tgregg)
#471 (ion) `IonReader` throws `AssertionError` for Timestamp value
 (contributed by @tgregg)
#473 (ion) `IonReader.next()` throws `ArrayIndexOutOfBoundsException` for some
  corrupt content
 (contributed by @tgregg)
#482 (ion): Upgrade `ion-java` to 1.11.2 and remove handling of exceptions that
  are no longer leaked
 (contributed by @tgregg)

2.16.2 (09-Mar-2024)

No changes since 2.16.1

2.16.1 (24-Dec-2023)

#303: `NullPointerException` in `IonParser.nextToken()`
 (reported by @ZanderHuang)

2.16.0 (15-Nov-2023)

#400: (avro) Rewrite Avro buffer recycling (`ApacheCodecRecycler.java`) to
  use new `RecyclerPool`, allow configuring use of non-ThreadLocal based pools
#403: (smile) Remove Smile-specific buffer-recycling
#410: (ion) Update `com.amazon.ion:ion-java` to 1.10.5 (from 1.9.5)
 (requested by Dominik B)

2.15.4 (15-Feb-2024)

No changes since 2.15.3

2.15.3 (12-Oct-2023)

#384: (smile) `Smile` decoding issue with `NonBlockingByteArrayParser`, concurrency
 (reported by Simon D)

2.15.2 (30-May-2023)

#379: (avro) `logback-test.xml` in wrong place (avro/src/main/resources)
 (reported by Kyle S)

2.15.1 (16-May-2023)

No changes since 2.15.0

2.15.0 (23-Apr-2023)

#347: (cbor) Add support for CBOR stringref extension (`CBORGenerator.Feature.STRINGREF`)
 (contributed by Aaron B)
#356: (cbor) Add `CBORGenerat.Feature.WRITE_MINIMAL_DOUBLES` for writing `double`s as `float`s
  if safe to do so
 (contributed by Aaron B)
#373: (cbor) Remove optimized `CBORParser.nextTextValue()` implementation

2.14.3 (05-May-2023)

#354: (all) Missing license file in Maven package for newer versions
 (reported by Philipp d-S)
#366: `CBORGenerator.writeRawUTF8String()` seems to ignore offset
 (reported by Nik E)

2.14.2 (28-Jan-2023)

No changes since 2.14.1

2.14.1 (21-Nov-2022)

#342: (smile) Possible performance improvement on jdk9+ for Smile decoding
 (contributed by Brian H)

2.14.0 (05-Nov-2022)

#301: (cbor, smile) Missing configuration methods for format-specific
  parser/generator features
#310: (avro) Avro schema generation: allow override namespace with new
  `@AvroNamespace` annotation
 (requested by fleetwoods@github)
 (contributed by Michal F)
#311: (ion) `IonObjectMapper` does not throw JacksonException for some invalid Ion
 (contributed by Matthew P)
#312: (cbor, smile) Short NUL-only keys incorrectly detected as duplicates
 (reported by David T)
#325: (ion) Ensure `IonReader` instances created within `IonFactory` are
  always resource-managed
 (contributed by Tyler G)
#338: Use passed "current value" in `writeStartObject()` overload
 (contributed by Szymon S)
#341: (ion) Update to Amazon Ion 1.9.5
 (suggested by Dominic B)

2.13.5 (23-Jan-2023)
2.13.4 (03-Sep-2022)

No changes since 2.13.3

2.13.3 (14-May-2022)

#317: (ion) IonValueDeserializer does not handle getNullValue correctly for a missing property
 (contributed by atokuzAmzn@github)

2.13.2 (06-Mar-2022)

No changes since 2.13.1

2.13.1 (19-Dec-2021)

#302: (ion) `IllegalArgumentException` in `IonParser.getEmbeddedObject()`
 (reported by ZanderHuang@github)

2.13.0 (30-Sep-2021)

#239: (cbor) Should validate UTF-8 multi-byte validity for short decode path too
#248: (ion) Deprecate `CloseSafeUTF8Writer`, remove use
#252: (smile) Make `SmileFactory` support `JsonFactory.Feature.CANONICALIZE_FIELD_NAMES`
#253: (cbor) Make `CBORFactory` support `JsonFactory.Feature.CANONICALIZE_FIELD_NAMES`
#264: (cbor) Handle case of BigDecimal with Integer.MIN_VALUE for scale gracefully
 (actual fix in `jackson-databind`)
#272: (cbor) Uncaught exception in CBORParser._nextChunkedByte2 (by ossfuzzer)
 (reported by Fabian M)
#273: (cbor) Another uncaught exception in CBORParser._nextChunkedByte2 (by ossfuzzer)
 (reported by Fabian M)
#276: (smile) Add `SmileGenerator.Feature.LENIENT_UTF_ENCODING` for lenient handling
  of broken Unicode surrogate pairs on writing
 (requested by kireet@github)
#283: (avro) Add `logicalType` support for some `java.time` types; add `AvroJavaTimeModule`
  for native ser/deser
 (contributed by Michal F)
#284: Support base64 strings in `getBinaryValue()` for CBOR and Smile
 (requested by Hunter H)
#289: (cbor) `ArrayIndexOutOfBounds` for truncated UTF-8 name
#290: (avro) Generate logicalType switch
 (contributed by Michal F)
#291: (smile) `ArrayIndexOutOfBounds` for truncated UTF-8 name
#295: (ion) `jackson-dataformat-ion` does not handle null.struct deserialization correctly
 (contributed by Martin G)
- `Ion-java` dep 1.5.1 -> 1.8.0
- Minor change to Ion module registration names (fully-qualified)

2.12.7 (26-May-2022)

No changes since 2.12.6

2.12.6 (15-Dec-2021)

#302: `IllegalArgumentException` in `IonParser.getEmbeddedObject()`
 (reported by ZanderHuang@github)
- (ion) Add missing `withDefaultImpl()` override for `IonAnnotationTypeResolverBuilder`
- `Ion-java` dep 1.8.0 -> 1.8.3

2.12.5 (27-Aug-2021)

No changes since 2.12.4

2.12.4 (06-Jul-2021)

#287: (cbor) Uncaught exception in CBORParser._nextChunkedByte2 (by ossfuzzer)
#288: (cbor) Uncaught exception in CBORParser._findDecodedFromSymbols() (by ossfuzzer)

2.12.3 (12-Apr-2021)

#257: (smile) Uncaught validation problem wrt Smile "BigDecimal" type
 (reported by Fabian M)
#258: (smile) ArrayIndexOutOfBoundsException for malformed Smile header
 (reported by Fabian M)
#259: (cbor) Failed to handle case of alleged String with length of Integer.MAX_VALUE
 (reported by Fabian M)
#260: (smile) Allocate byte[] lazily for longer Smile binary data payloads
 (reported by Fabian M)
#261 (cbor) CBORParser need to validate zero-length byte[] for BigInteger 
 (reported by Fabian M)
#263: (smile) Handle invalid chunked-binary-format length gracefully
 (reported by Fabian M)
#265: (smile) Allocate byte[] lazily for longer Smile binary data payloads
 (7-bit encoded) 
#266: (smile)  ArrayIndexOutOfBoundsException in SmileParser._decodeShortUnicodeValue()
 (reported by Fabian M)
#268: (smile) Handle sequence of Smile header markers without recursion
 (reported by Fabian M)
#269: (cbor) CBOR loses `Map` entries with specific `long` Map key values (32-bit boundary)
 (reported by Quantum64@github)
#270: (ion) Ion Polymorphic deserialization in 2.12 breaks wrt use of Native Type Ids
  when upgrading from 2.8
 (contributed by Nick)
 
2.12.2 (03-Mar-2021)

#236: (cbor) `ArrayIndexOutOfBoundsException` in `CBORParser` for invalid UTF-8 String
 (reported by Fabian M)
#240: (cbor) Handle invalid CBOR content like `[ 0x84 ]` (incomplete array)
#241: (ion) Respect `WRITE_ENUMS_USING_TO_STRING` in `EnumAsIonSymbolSerializer`
 (contributed by jhhladky@github)
#242: (ion) Add support for generating IonSexps
 (contributed by jhhladky@github)
#244: (ion) Add support for deserializing IonTimestamps and IonBlobs
 (contributed by jessbrya-amzn@github)
#246: (ion) Add `IonObjectMapper.builderForBinaryWriters()` / `.builderforTextualWriters()`
  convenience methods
 (contributed by Michael L)
#247: (ion) Enabling pretty-printing fails Ion serialization

2.12.1 (08-Jan-2021)

#232: (ion) Allow disabling native type ids in IonMapper
 (contributed by Josh B)
#235: (smile) Small bug in byte-alignment for long field names in Smile,
  symbol table reuse

2.12.0 (29-Nov-2020)

#204: (ion) Add `IonFactory.getIonSystem()` accessor
 (contributed by Paul F)
#212: (ion) Optimize `IonParser.getNumberType()` using `IonReader.getIntegerSize()`
 (contributed by Michael L)
#222: (cbor) Add `CBORGenerator.Feature.LENIENT_UTF_ENCODING` for lenient handling of
  Unicode surrogate pairs on writing
 (contributed by Guillaume B)
#228: (cbor) Add support for decoding unassigned "simple values" (type 7)
 (requested by davidlepilote@github)
- Add Gradle Module Metadata (https://blog.gradle.org/alignment-with-gradle-module-metadata)

2.11.4 (12-Dec-2020)

#186: (cbor) Eager allocation of byte buffer can cause `java.lang.OutOfMemoryError`
   exception (CVE-2020-28491)
 (reported by Paul A)

2.11.3 (02-Oct-2020)

#219: (avro) Cache record names to avoid hitting class loader
 (contributed by Marcos P)

2.11.2 (02-Aug-2020)

#216: (avro) Avro null deserialization
 (fix contributed by Marcos P)

2.11.1 (25-Jun-2020)

#204: (ion) Add `IonFactory.getIonSystem()` accessor
 (contributed by Paul F)

2.11.0 (26-Apr-2020)

#179: (avro) Add `AvroGenerator.canWriteBinaryNatively()` to support binary writes,
  fix `java.util.UUID` representation
#192: (ion) Allow `IonObjectMapper` with class name annotation introspector to deserialize
  generic subtypes
 (reported, fix provided by Binh T)
#195: Remove dependencies upon Jackson 1.X and Avro's JacksonUtils
 (contributed by Bryan H)
#198: `jackson-databind` should not be full dependency for (cbor, protobuf, smile)
  modules
#201: `CBORGenerator.Feature.WRITE_MINIMAL_INTS` does not write most compact form
  for all integers
 (reported by Jonas K)
- `AvroGenerator` overrides `getOutputContext()` properly

2.10.5 (21-Jul-2020)

#204: (ion) Add `IonFactory.getIonSystem()` accessor
 (contributed by Paul F)
#211: (avro) Fix schema evolution involving maps of non-scalar
 (fix contributed by Marcos P)

2.10.4 (03-May-2020)

#202: (protobuf) Parsing a protobuf message doesn't properly skip unknown fields
 (reported by dmitry-timin@github)

2.10.3 (03-Mar-2020)

No changes since 2.10.2

2.10.2 (05-Jan-2020)

#189: (ion) IonObjectMapper close()s the provided IonWriter unnecessarily
 (reported, fix contributed by Zack S)
- ion-java dependency 1.4.0 -> 1.5.1

2.10.1 (09-Nov-2019)

#185: (cbor) Internal parsing of tagged arrays can lead to stack overflow
 (reported by Paul A)
#188: (cbor) Unexpected `MismatchedInputException` for `byte[]` value bound to `String`
  in collection/array (actual fix in `jackson-databind`)
 (reported by Yanming Z)

2.10.0 (26-Sep-2019)

#139: (cbor) Incorrect decimal fraction representation
 (reported by wlukowicz@github)
#148: (protobuf) Add `ProtobufMapper.generateSchemaFor(TypeReference<?>)` overload
 (suggested by MrThreepwood@github)
#155: (cbor, smile) Inconsistent support for FLUSH_PASSED_TO_STREAM
 (reported, fix suggested by Carter K)
#157: (all) Add simple module-info for JDK9+, using Moditect
#163: (ion) Update `ion-java` dependency
 (contributed by Fernando R-B)
#168: (avro) `JsonMappingException` for union types with multiple Record types
 (reported by Juliana A; fix contributed by Marcos P)
#173: (avro) Improve Union type serialization performance
 (fix contributed by Marcos P)
#177: (avro) Deserialization of "empty" Records as root values fails
 (reported by Macros P)
#178: (cbor) Fix issue wit input offsets when parsing CBOR from `InputStream`
 (reported by iziamos@github)
#180: (protobuf) Add `ProtobufGenerator.canWriteBinaryNatively()` to support binary writes
- asm version upgrade to 6.2.1 (from 5.1)
- (cbor, smile) Rewrote handling of "output context" for better field id write support

2.9.10 (21-Sep-2019)

No changes since 2.9.9

2.9.9 (16-May-2019)

#159: (cbor) Some short UTF Strings encoded using non-canonical form
 (reported by Alexander C)
#161: (avro) Deserialize from newer version to older one throws NullPointerException
 (reported, fix contributed by Łukasz D)

2.9.8 (15-Dec-2018)

#140: (protobuf) Stack overflow when generating Protobuf schema  on class with
   cyclic type definition
 (reported by acommuni@github)
#153: (smile) Unable to set a compression input/output decorator to a `SmileFactory`
 (reported by Guido M)

2.9.7 (19-Sep-2018)

#142: (ion) `IonParser.getNumberType()` returns `null` for `IonType.FLOAT`
 (contributed by Michael M)
#150: Add `CBORMapper`
#151: Add `SmileMapper`

2.9.6 (12-Jun-2018)

#93: (cbor) `CBORParser` does not accept "undefined value"
 (reported by mbaril@github)
#135: (protobuf) Infinite sequence of `END_OBJECT` tokens returned at end of streaming read
 (reported by Leo W)
#136: (avro) Fix MapWriteContext not correctly resolving union values
 (contributed by baharclerode@github)

2.9.5 (26-Feb-2018)

#128 (protobuf) Fix skip unknown WireType.FIXED_64BIT value bug
 (reported, contributed fix for by marsqing@github@github)
#129 (cbor) Remove "final" modifier from `CBORParser`
  (suggested by jinzha@github)

2.9.4 (24-Jan-2018)

No changes since 2.9.3

2.9.3 (09-Dec-2017)

#114: (cbor) copyStructure(): avoid duplicate tags when copying tagged binary.
 (contributed by philipa@github)
#116: (protobuf) Should skip the positive byte which is the last byte of an varint
 (contributed by marsqing@github)
#124: Invalid value returned for negative int32 where the absolute value is > 2^31 - 1
 (repoted by Jacek L)
- (protobuf) Minor fix to skipping with `nextFieldName()`
- (avro) Fix a typo in SPI Metadata (META-INF/services/com.fasterxml.jackson.core.JsonFactory)

2.9.2 (14-Oct-2017)

#113 (avro): incorrect deserialization of `long` with new `AvroFactory`
 (reported by LvR@github)

2.9.1 (07-Sep-2017)

#102 (ion): Make IonValueModule public for use outside of IonValueMapper

2.9.0 (30-Jul-2017)

#13 (avro): Add support for Avro default values
#14 (avro): Add support for Avro annotations via `AvroAnnotationIntrospector`
 (contributed by baharclerode@github)
#15 (avro): Add a way to produce "file" style Avro output
#56 (avro): Replace use of `BinaryDecoder` with direct access
#57 (avro): Add support for @Stringable annotation
 (contributed by baharclerode@github)
#59 (avro): Add support for @AvroAlias annotation for Record/Enum name evolution
 (contributed by baharclerode@github)
#60 (avro): Add support for `@Union` and polymorphic types
 (contributed by baharclerode@github)
#63 (avro): Implement native `float` handling for parser
#64 (proto): Implement native `float` handling for parser
#68 (proto): Getting "type not supported as root type by protobuf" for serialization
  of short and UUID types
 (reported by Eldad R)
#69 (avro): Add support for `@AvroEncode` annotation
#79 (proto): Fix wire type for packed arrays
#95 (avro): Add new method, `withUnsafeReaderSchema` in `AvroSchema` to allow avoiding verification exception
#98 (avro): AvroMapper with Map throwing UnsupportedOperationException
 (reported by coder-hub@github)
- (avro): Upgrade `avro-core` dep from 1.7.7 to 1.8.1

2.8.11 (24-Dec-2017)

#106: (protobuf) fix calling _skipUnknownValue() twice
 (reported, contributed fix for by marsqing@github@github)
#108: (protobuf) fix NPE in skip unknown nested key
#126: (protobuf) always call checkEnd() when skip unknown field

2.8.10 (24-Aug-2017)

#94: Should _ensureRoom in ProtobufGenerator.writeString()
 (reported by marsqing@github)

2.8.9 (12-Jun-2017)

#72: (protobuf) parser fails with /* comment */
#85: (protobuf) _decode32Bits() bug in ProtobufParser
 (reported by marsqing@github)

2.8.8 (05-Apr-2017)

#54 (protobuf): Some fields are left null
#58 (avro): Regression due to changed namespace of inner enum types
 (reported by Peter R)
#62: (cbor) `java.lang.ArrayIndexOutOfBoundsException` at `CBORGenerator.java`:548
#67: (proto) Serialization of multiple nesting levels has issues
#70: (proto) Can't deserialize packed repeated field
 (reported by Kenji N)

2.8.7 (21-Feb-2017)

#34 (avro): Reading Avro with specified reader and writer schemas
 (requested by Pawel S)
#35 (avro): Serialization of multiple objects (`SequenceWriter`
 (reported by tomvandenberge@github)
#38 (avro): Deserialization of multiple (root) values from Avro
#39 (avro): Problem decoding Maps with union values
#43 (cbor): Buffer size dependency in `UTF8JsonGenerator writeRaw(...)`
 (reported by Christopher C)

2.8.6 (12-Jan-2017)
2.8.5 (14-Nov-2016)

#30 (cbor): Overflow when decoding uint32 for Major type 0
 (reported by TianlinZhou@github)
#31 (cbor): Exception serializing double[][]

2.8.4 (14-Oct-2016)

No changes since 2.8.3

2.8.3 (17-Sep-2016)

#28 (avro): Java float deserialized as `DoubleNode` instance
 (reported by teabot@github)

2.8.2 (30-Aug-2016)

#27 (protobuf): Fixed long deserialization problem for longs of ~13digit length
 (contributed by Michael Z)

2.8.1 (20-Jul-2016)

- (protobuf) Add optimized ProtobufParser.nextTextValue() implementation

2.8.0 (04-Jul-2016)

#16: (cbor) Implement `JsonGenerator.writeArray()` methods added in `jackson-core` (2.8)
#17: (cbor) Support parsing of `BigInteger`, `BigDecimal`, not just generating
#18: (cbor) Fail to report error for trying to write field name outside Object (root level)
#19: (smile) Fail to report error for trying to write field name outside Object (root level)
#24: (cbor) Incorrect coercion for int-valued Map keys to String
- (protobuf) Support `writeArray()` for `int[]`, `long[]` and `double[]`
- (protobuf) Add `ProtobufMapper.generateSchemaFor(type)` helper methods
