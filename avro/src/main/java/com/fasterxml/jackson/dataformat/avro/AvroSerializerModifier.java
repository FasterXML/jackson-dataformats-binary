package com.fasterxml.jackson.dataformat.avro;

import java.util.*;

import org.apache.avro.specific.SpecificRecordBase;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

/**
 * Serializer modifier used to suppress serialization of "schema"
 * property for Avro-generated types.
 *
 * @since 2.7.2
 */
public class AvroSerializerModifier
    extends BeanSerializerModifier
{
    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
    {
        AnnotatedClass ac = beanDesc.getClassInfo();
        // Couple of ways to determine if it's generated class: main alternative
        // would be to look for annotation `AvroGenerated` but check for base
        // class seems simpler and as robust:
        
        if (SpecificRecordBase.class.isAssignableFrom(ac.getRawType())) {
            Iterator<BeanPropertyWriter> it = beanProperties.iterator();
            while (it.hasNext()) {
                BeanPropertyWriter prop = it.next();
                if ("schema".equals(prop.getName())) {
                    it.remove();
                    break;
                }
            }
        }
        return beanProperties;
    }
}
