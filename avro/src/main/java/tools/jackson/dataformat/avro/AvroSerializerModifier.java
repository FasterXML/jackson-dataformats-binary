package tools.jackson.dataformat.avro;

import java.util.Iterator;
import java.util.List;

import org.apache.avro.specific.SpecificRecordBase;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Serializer modifier used to suppress serialization of "schema"
 * property for Avro-generated types.
 *
 * @since 2.7.2
 */
public class AvroSerializerModifier
    extends ValueSerializerModifier
{
    private static final long serialVersionUID = 1L;

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BeanDescription.Supplier beanDescRef, List<BeanPropertyWriter> beanProperties)
    {
        // Couple of ways to determine if it's generated class: main alternative
        // would be to look for annotation `AvroGenerated` but check for base
        // class seems simpler and as robust:

        if (SpecificRecordBase.class.isAssignableFrom(beanDescRef.getBeanClass())) {
            Iterator<BeanPropertyWriter> it = beanProperties.iterator();
            while (it.hasNext()) {
                BeanPropertyWriter prop = it.next();
                if ("schema".equals(prop.getName()) || "specificData".equals(prop.getName())) {
                    it.remove();
                }
            }
        }
        return beanProperties;
    }
}
