/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Module that causes all enums to be serialized as Ion symbols.
 */
public class EnumAsIonSymbolModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    public EnumAsIonSymbolModule() {
        super("EnumAsIonSymbolModule", PackageVersion.VERSION);
        addSerializer(new EnumAsIonSymbolSerializer());
    }
}
