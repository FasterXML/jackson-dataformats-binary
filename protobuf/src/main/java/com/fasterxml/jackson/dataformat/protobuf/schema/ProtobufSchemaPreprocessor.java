package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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
 * field declarations (only within {@code proto3} message / extend bodies) and
 * injects a synthetic {@code optional} label so the underlying parser accepts
 * them; downstream resolution maps {@code optional} to
 * {@code required == repeated == false}, which matches {@code proto3} singular
 * semantics.
 *<p>
 * Constructs that are <i>not</i> label-less fields -- nested type declarations,
 * {@code oneof} bodies (whose fields are already label-less and handled by the
 * parser), {@code enum} constants, options, {@code reserved}, etc. -- are left
 * untouched.
 *<p>
 * NOTE: {@code map<K,V>} fields are recognized here only to fail with a clear
 * error message; full map support is not yet implemented (see
 * <a href="https://github.com/FasterXML/jackson-dataformats-binary/issues/708">#708</a>).
 *
 * @since 2.21.6
 */
class ProtobufSchemaPreprocessor
{
    /** Matches a {@code syntax = "proto3";} declaration (single or double quotes). */
    private static final Pattern PROTO3_SYNTAX = Pattern.compile(
            "syntax\\s*=\\s*[\"']proto3[\"']");

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
     * Rewrites given schema so that {@code proto3} label-less fields parse with
     * the bundled parser. Non-{@code proto3} schemas are returned unchanged.
     */
    public static String preprocess(String schema) {
        if (schema == null || !PROTO3_SYNTAX.matcher(schema).find()) {
            return schema;
        }
        return new ProtobufSchemaPreprocessor(schema)._process();
    }

    private String _process() {
        // Stack of "is the enclosing block a message/extend body?" flags; the
        // file level (bottom) is not, so it starts as false.
        final Deque<Boolean> inFieldBody = new ArrayDeque<Boolean>();
        inFieldBody.push(Boolean.FALSE);
        // Whether the block opened by the NEXT '{' will be a message/extend body
        boolean pendingFieldBody = false;
        boolean atStmtStart = true;

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
                _copyQuotedString(c);
                atStmtStart = false;
                continue;
            }
            if (c == '{') {
                _out.append(c);
                _ptr++;
                inFieldBody.push(pendingFieldBody);
                pendingFieldBody = false;
                atStmtStart = true;
                continue;
            }
            if (c == '}') {
                _out.append(c);
                _ptr++;
                if (inFieldBody.size() > 1) {
                    inFieldBody.pop();
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

                if (inFieldBody.peek().booleanValue()) {
                    if ("map".equals(word)) {
                        throw new IllegalArgumentException(String.format(
                                "Unsupported proto3 `map` field at line %d: `map` type is not yet"
                                + " supported by jackson-dataformats-binary (see"
                                + " https://github.com/FasterXML/jackson-dataformats-binary/issues/708)",
                                _lineNumber()));
                    }
                    if (!NON_FIELD_KEYWORDS.contains(word)) {
                        // Label-less field: inject synthetic label
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

    private void _copyQuotedString(char quote) {
        _out.append(_data[_ptr++]); // opening quote
        while (_ptr < _end) {
            char c = _data[_ptr++];
            _out.append(c);
            if (c == '\\' && _ptr < _end) {
                _out.append(_data[_ptr++]); // keep escaped char as-is
            } else if (c == quote) {
                break;
            }
        }
    }

    private String _readWord() {
        int start = _ptr;
        while (_ptr < _end && _isWordPart(_data[_ptr])) {
            _ptr++;
        }
        return new String(_data, start, _ptr - start);
    }

    private int _lineNumber() {
        int line = 1;
        for (int i = 0, end = Math.min(_ptr, _end); i < end; ++i) {
            if (_data[i] == '\n') {
                ++line;
            }
        }
        return line;
    }

    private static boolean _isWordStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean _isWordPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }
}
