Project: jackson-datatypes-binaryModules:
  jackson-dataformat-avro
  jackson-dataformat-cbor
  jackson-dataformat-protobuf
  jackson-dataformat-smile

------------------------------------------------------------------------
=== Releases ===
------------------------------------------------------------------------

2.11.4 (not yet released)

#186: (cbor) Eager allocation of byte buffer can cause `java.lang.OutOfMemoryError`
   exception
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
