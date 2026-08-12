package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper that rewrites a {@code .proto} schema so that the (older) bundled
 * {@code protoparser} grammar can parse constructs that are standard in
 * {@code proto3} but were not yet part of the grammar shipped with
 * {@code protoparser 4.0.3}.
 *<p>
 * Specifically, {@code proto3} declares "singular" fields without a leading
 * label ({@code required} / {@code optional} / {@code repeated}), for example:
 *<pre>
 *   syntax = "proto3";
 *   message Point {
 *     int32 x = 1;
 *     string label = 2;
 *   }
 *</pre>
 * whereas {@code protoparser 4.0.3} only recognizes a statement as a field when
 * it begins with one of those labels. This preprocessor detects such label-less
 * field declarations in message / extend bodies and injects a synthetic
 * {@code optional} label so the underlying parser accepts them (for ordinary
 * fields, only when the schema is {@code proto3}); downstream resolution maps
 * {@code optional} to {@code required == repeated == false}, which matches
 * {@code proto3} singular semantics.
 *<p>
 * Constructs that are <i>not</i> label-less fields -- nested type declarations,
 * {@code oneof} bodies (whose fields are already label-less and handled by the
 * parser), {@code enum} constants, options, {@code reserved}, etc. -- are left
 * untouched.
 *<p>
 * NOTE: {@code map<K,V>} fields are also label-less (in both {@code proto2} and
 * {@code proto3}), so a synthetic label is injected for them as well, purely so
 * they parse. Turning a {@code map} field into its actual (repeated entry) form is a
 * semantic concern handled downstream during type resolution ({@link TypeResolver},
 * see <a href="https://github.com/FasterXML/jackson-dataformats-binary/issues/712">#712</a>),
 * not here.
 *
 * @since 2.21.6
 */
class ProtobufSchemaPreprocessor
{
    /**
     * Statement-leading keywords that may appear in a message / extend body but
     * do NOT begin a label-less field, and so must never receive an injected label.
     */
    private static final Set<String> NON_FIELD_KEYWORDS = new HashSet<String>(Arrays.asList(
            "message", "enum", "oneof", "extend", "group",
            "option", "reserved", "extensions",
            "required", "optional", "repeated"
    ));

    private final char[] _data;
    private final int _end;
    private final StringBuilder _out;

    private int _ptr;

    private ProtobufSchemaPreprocessor(String schema) {
        _data = schema.toCharArray();
        _end = _data.length;
        _out = new StringBuilder(_data.length + 32);
    }

    /**
     * Rewrites given schema so that {@code proto3} label-less fields -- and
     * label-less {@code map<K,V>} fields in either syntax -- parse with the
     * bundled parser. (Map fields are then resolved to their entry form during type
     * resolution; see the class comment.) Schemas that need no rewriting are returned
     * unchanged.
     */
    public static String preprocess(String schema) {
        // Fast path: label injection only matters for proto3, and map handling
        // only when a `map` field is present; if the text mentions neither
        // "proto3" nor "map" there is nothing to rewrite. (A false positive here
        // just means we scan and copy the text through unchanged.)
        if (schema == null
                || (schema.indexOf("proto3") < 0 && schema.indexOf("map") < 0)) {
            return schema;
        }
        return new ProtobufSchemaPreprocessor(schema)._process();
    }

    private String _process() {
        // One bit per brace-nesting level: bit N is set when the block at depth
        // N is a message/extend body (where label-less fields may appear). The
        // file level (depth 0) is never a field body.
        final BitSet inFieldBody = new BitSet();
        int depth = 0;
        // Whether the block opened by the NEXT '{' will be a message/extend body
        boolean pendingFieldBody = false;
        boolean atStmtStart = true;
        // Detected from the `syntax = "..."` declaration during the scan -- which,
        // unlike a raw regex over the text, correctly ignores comments and strings.
        // The syntax statement precedes any message body, so this is known before
        // the first field-injection decision.
        boolean isProto3 = false;
        boolean expectSyntaxValue = false;

        while (_ptr < _end) {
            final char c = _data[_ptr];

            // Comments and strings are copied verbatim; they never start a statement
            if (c == '/' && _ptr + 1 < _end && _data[_ptr + 1] == '/') {
                _copyLineComment();
                continue;
            }
            if (c == '/' && _ptr + 1 < _end && _data[_ptr + 1] == '*') {
                _copyBlockComment();
                continue;
            }
            if (c == '"' || c == '\'') {
                String content = _copyQuotedString(c);
                if (expectSyntaxValue) {
                    isProto3 = "proto3".equals(content);
                    expectSyntaxValue = false;
                }
                atStmtStart = false;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                _out.append(c);
                _ptr++;
                continue;
            }
            if (c == '{') {
                _out.append(c);
                _ptr++;
                inFieldBody.set(depth, pendingFieldBody);
                depth++;
                pendingFieldBody = false;
                atStmtStart = true;
                continue;
            }
            if (c == '}') {
                _out.append(c);
                _ptr++;
                if (depth > 0) {
                    depth--;
                }
                pendingFieldBody = false;
                atStmtStart = true;
                continue;
            }
            if (c == ';') {
                _out.append(c);
                _ptr++;
                atStmtStart = true;
                continue;
            }

            // A statement may begin with an identifier, or (rarely) a fully-qualified
            // type name with a leading dot, e.g. `.foo.Bar name = 1;`
            if (atStmtStart && (_isWordStart(c) || c == '.')) {
                final String word = _readWord();
                pendingFieldBody = "message".equals(word) || "extend".equals(word);
                if ("syntax".equals(word)) {
                    expectSyntaxValue = true;
                }

                if (depth > 0 && inFieldBody.get(depth - 1)) {
                    // A `map` field is always label-less (in both proto2 and proto3);
                    // other label-less fields occur only in proto3 (proto2 requires
                    // explicit labels, so a label-less field there is a genuine error).
                    // Injecting a label lets the field parse; `map` fields are then
                    // resolved to their entry form during type resolution (see #712).
                    boolean labelless = "map".equals(word)
                            || (isProto3 && !NON_FIELD_KEYWORDS.contains(word));
                    if (labelless) {
                        _out.append("optional ");
                    }
                }
                _out.append(word);
                atStmtStart = false;
                continue;
            }

            // Any other character: copy through, no longer at statement start
            _out.append(c);
            _ptr++;
            atStmtStart = false;
        }
        return _out.toString();
    }

    private void _copyLineComment() {
        // copy "//" and rest of line (newline handled by main loop)
        while (_ptr < _end && _data[_ptr] != '\n') {
            _out.append(_data[_ptr++]);
        }
    }

    private void _copyBlockComment() {
        _out.append(_data[_ptr++]); // '/'
        _out.append(_data[_ptr++]); // '*'
        while (_ptr < _end) {
            char c = _data[_ptr++];
            _out.append(c);
            if (c == '*' && _ptr < _end && _data[_ptr] == '/') {
                _out.append(_data[_ptr++]); // '/'
                break;
            }
        }
    }

    /**
     * Copies a quoted string through verbatim and returns its (unescaped-agnostic)
     * inner content -- used only to read the {@code syntax} declaration's value.
     */
    private String _copyQuotedString(char quote) {
        StringBuilder content = new StringBuilder();
        _out.append(_data[_ptr++]); // opening quote
        while (_ptr < _end) {
            char c = _data[_ptr++];
            _out.append(c);
            if (c == '\\' && _ptr < _end) {
                _out.append(_data[_ptr++]); // keep escaped char as-is
            } else if (c == quote) {
                break;
            } else {
                content.append(c);
            }
        }
        return content.toString();
    }

    private String _readWord() {
        int start = _ptr;
        while (_ptr < _end && _isWordPart(_data[_ptr])) {
            _ptr++;
        }
        return new String(_data, start, _ptr - start);
    }

    private static boolean _isWordStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean _isWordPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }
}
