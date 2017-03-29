package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;

public class DefinedTypeElementBuilders
{
    protected Map<JavaType, TypeElementBuilder> _definedTypeElementBuilders = new LinkedHashMap<>();

    protected Set<JavaType> _isNestedType = new HashSet<JavaType>();

    public DefinedTypeElementBuilders() { }

    public void addTypeElement(JavaType type, TypeElementBuilder builder, boolean isNested) {
        if (_definedTypeElementBuilders.containsKey(type)) { //Type element builder already defined
            if (_definedTypeElementBuilders.get(type) != builder) { //Not expect this. 
                throw new IllegalStateException("Trying to redefine TypeElementBuilder for type " + type);
            }
        } else { //new builder
            _definedTypeElementBuilders.put(type, builder);
        }
		
        if (isNested) {
            _isNestedType.add(type);
        }
    }
    
    public boolean containsBuilderFor(JavaType type) {
        return _definedTypeElementBuilders.containsKey(type);
    }

    public TypeElementBuilder getBuilderFor(JavaType type) {
        return _definedTypeElementBuilders.get(type);
    }

    public Set<TypeElementBuilder> getAllBuilders() {
        return new HashSet<TypeElementBuilder>(_definedTypeElementBuilders.values());
    }

    public Set<TypeElementBuilder> getAllNestedBuilders() {
        return getAllBuildersFor(_isNestedType);
    }
	
    public Set<TypeElementBuilder> getDependencyBuilders() {
        return getNonNestedBuilders(true); 
    }
	
    public Set<TypeElementBuilder> getNonNestedBuilders() {
        return getNonNestedBuilders(false);
    }

    public Set<TypeElementBuilder> getNonNestedBuilders(boolean excludeRoot) {
        Set<JavaType> types = _definedTypeElementBuilders.keySet(); //all keys
        types.removeAll(_isNestedType); //exclude nested type
		
        if(excludeRoot) { //exclude root
            if(_definedTypeElementBuilders.isEmpty()) {
                throw new IllegalStateException("DefinedTypeElementBuilders._definedTypeElementBuilders is empty");
            }
            types.remove(_definedTypeElementBuilders.keySet().iterator().next()); //expect the first element is root
        }
        return getAllBuildersFor(types);
    }

    protected HashSet<TypeElementBuilder> getAllBuildersFor(Collection<JavaType> types) {
        HashSet<TypeElementBuilder> nestedBuilder = new HashSet<>();
        for (JavaType type : types) {
            nestedBuilder.add(getBuilderFor(type));
        }
        return nestedBuilder;
    }
}
