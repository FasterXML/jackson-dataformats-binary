package com.fasterxml.jackson.dataformat.protobuf;

public class BigNumPair {
    public static final String protobuf_str =
            "message BigNumPair {\n"
                    + " required int64 long1 = 1;\n"
                    + " required int64 long2 = 2;\n"
                    + "}\n";

    public long long1;
    public long long2;
}
