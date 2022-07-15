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

package tools.jackson.dataformat.ion;

import tools.jackson.core.Version;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleSerializers;

/**
 * Module that causes all enums to be serialized as Ion symbols.
 *<p>
 * As of Jackson 3.0 does not extend {@code SimpleModule} to keep it {@link java.io.Serializable}
 * (value serializers, deserializers are not serializable in 3.0).
 */
public class EnumAsIonSymbolModule extends JacksonModule
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    public EnumAsIonSymbolModule() { }

    @Override
    public void setupModule(SetupContext context)
    {
        context.addSerializers(new SimpleSerializers()
                .addSerializer(new EnumAsIonSymbolSerializer())
        );
    }

    @Override
    public String getModuleName() {
        return getClass().getName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
}
