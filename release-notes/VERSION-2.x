Project: jackson-datatypes-binary
Modules:
  jackson-dataformat-avro
  jackson-dataformat-cbor
  jackson-dataformat-ion (since 2.9)
  jackson-dataformat-protobuf
  jackson-dataformat-smile

------------------------------------------------------------------------
=== Releases ===
------------------------------------------------------------------------

2.13.0 (not yet released)

#239: (cbor) Should validate UTF-8 multi-byte validity for short decode path too
#248: (ion) Deprecate `CloseSafeUTF8Writer`, remove use
#252: (smile) Make `SmileFactory` support `JsonFactory.Feature.CANONICALIZE_FIELD_NAMES`
#253: (cbor) Make `CBORFactory` support `JsonFactory.Feature.CANONICALIZE_FIELD_NAMES`
#264: (cbor) Handle case of BigDecimal with Integer.MIN_VALUE for scale gracefully
 (actual fix in `jackson-databind`)
#272: (cbor) Uncaught exception in CBORParser._nextChunkedByte2 (by ossfuzzer)
 (reported by Fabian M)
- `Ion-java` dep 1.4.0 -> 1.8.0
- Minor change to Ion module registration names (fully-qualified)

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
