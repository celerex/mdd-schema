package be.celerex.mdd.schema.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import be.celerex.mdd.core.MDDParser;
import be.celerex.mdd.core.MDDSyntaxException;
import be.celerex.mdd.core.STLMetaProvider;
import be.celerex.mdd.schema.api.SchemaResolver;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.property.PropertyFactory;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeRegistryImpl;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.ModifiableElement;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeRegistry;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.Duration;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

public class MDDSchemaParser {
	
	private SimpleTypeWrapper wrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
	
	private List<String> reservedProperties = List.of("extends", "type");
	
	private String id;
	
	// this is the only reserved root word, no type can be defined with this name
	private final static String META_KEY = "$meta";
	
	private boolean includeDefaultGlobalNamespace = true;
	
	private SchemaResolver resolver = new URLSchemaResolver();

	public SchemaResolver getResolver() {
		return resolver;
	}

	public void setResolver(SchemaResolver resolver) {
		this.resolver = resolver;
	}

	public boolean isIncludeDefaultGlobalNamespace() {
		return includeDefaultGlobalNamespace;
	}

	public void setIncludeDefaultGlobalNamespace(boolean includeDefaultGlobalNamespace) {
		this.includeDefaultGlobalNamespace = includeDefaultGlobalNamespace;
	}
	
	private TypeRegistry importSchema(String uri) throws MDDSchemaException, MDDSyntaxException {
		try {
			String schema = resolver.getSchema(new URI(uri));
			MDDSchemaParser nonGlobalParser = new MDDSchemaParser();
			nonGlobalParser.setIncludeDefaultGlobalNamespace(includeDefaultGlobalNamespace);
			return nonGlobalParser.parse(schema);
		}
		catch (URISyntaxException e) {
			throw new MDDSchemaException("Can not parse global namespace", e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public TypeRegistry parse(String content) throws MDDSchemaException, MDDSyntaxException {
		TypeRegistryImpl registry = new TypeRegistryImpl();
		// always prefill "string", it is the only constant
		DefinedSimpleType<String> string = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class);
		if (string == null) {
			throw new MDDSchemaException("Can not resolve a simple type for 'string'");
		}
		registry.register(new be.nabu.libs.types.simple.String() {
			@Override
			public String getNamespace(Value<?>...values) {
				return null;
			}
		});
		if (includeDefaultGlobalNamespace) {
			registry.register(parseGlobal());
		}
		MDDParser parser = new MDDParser();
		STLMetaProvider metaProvider = new STLMetaProvider();
		metaProvider.setMetaKey(META_KEY);
		parser.setMetaProvider(metaProvider);

		// must be a map
		Object object = parser.parse(content);
		
		if (object instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) object;
			Map<String, Object> metadata = metaProvider.getMetadata(map);
			// first resolve imports
			if (metadata != null && metadata.containsKey("import")) {
				resolveImport(registry, metadata.get("import"));
			}
			parseMap(registry, map);
		}
		else {
			throw new MDDSchemaException("Expecting a map of types, received: " + object);
		}
		
		return registry;
	}

	@SuppressWarnings("unchecked")
	private void resolveImport(TypeRegistryImpl registry, Object importContent) throws MDDSchemaException, MDDSyntaxException {
		if (importContent != null) {
			// it could be a basic string
			if (importContent instanceof String) {
				registry.register(importSchema((String) importContent));
			}
			// you can make it a map which should have a uri in it
			else if (importContent instanceof Map) {
				Object importUri = ((Map<String, Object>) importContent).get("uri");
				if (importUri == null) {
					throw new MDDSchemaException("Could not find uri attribute for import: " + importContent);
				}
				else if (!(importUri instanceof String)) {
					throw new MDDSchemaException("Invalid uri value: " + importUri);
				}
				registry.register(importSchema((String) importUri));
			}
			else if (importContent instanceof List) {
				for (Object single : (List<?>) importContent) {
					resolveImport(registry, single);
				}
			}
		}
	}

	private List<String> parseId(String fullId) {
		int index = fullId.lastIndexOf('.');
		String namespace, name;
		if (index > 0) {
			namespace = fullId.substring(0, index);
			name = fullId.substring(index + 1);
		}
		else {
			namespace = null;
			name = fullId;
		}
		return new ArrayList<String>(Arrays.asList(namespace, name));
	}
	
	private Type lookup(TypeRegistry registry, String typeId) {
		List<String> parsed = parseId(typeId);
		return lookup(registry, parsed.get(0), parsed.get(1));
	}
	
	private Type lookup(TypeRegistry registry, String namespace, String name) {
		ComplexType complexType = registry.getComplexType(namespace, name);
		if (complexType != null) {
			return complexType;
		}
		return registry.getSimpleType(namespace, name);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void parseMap(TypeRegistryImpl registry, Map<String, Object> map) throws MDDSchemaException {
		Map<String, Object> globalMetadata = (Map<String, Object>) ((Map<String, Object>) map).get(META_KEY);
		if (globalMetadata == null) {
			globalMetadata = new HashMap<String, Object>();
		}
		String namespace = (String) globalMetadata.get("namespace");
		// root level definitions MUST be in order or dependency, if A extends B, you must define B first, then A
		// elements without an extends are assumed to be new complex types
		// otherwise we based it on the extends to see if it is a simple type or complex type. there are no complex-simple types
		// we still do the parsing in two phases, the first phase is to register all the types
		// in the second we actually start parsing
		// because in our A extends B example, in theory B could contain a child element that refers to A
		
		Map<String, Type> types = new HashMap<String, Type>();
		for (String key : map.keySet()) {
			if (key.equals(META_KEY)) {
				continue;
			}
			Object object = map.get(key);
			if (!(object instanceof Map)) {
				throw new MDDSchemaException("Expecting only complex elements");
			}
			// we assume you are a complex type
			boolean complex = true;
			Type superType = null;
			Map<String, Object> metadata = (Map<String, Object>) ((Map<String, Object>) object).get(META_KEY);
			if (metadata != null) {
				String extendsId = (String) metadata.get("extends");
				if (extendsId != null) {
					List<String> parsed = parseId(extendsId);
					// if we are in the global namespace, check if it is a native type
					if (parsed.get(0) == null) {
						superType = getNativeType(parsed.get(1));
					}
					if (superType == null) {
						superType = lookup(registry, parsed.get(0), parsed.get(1));
					}
					if (superType == null) {
						throw new MDDSchemaException("Can not resolve the referenced super type: " + extendsId);
					}
					complex = superType instanceof ComplexType;
				}
			}
			String typeId = (id == null ? (namespace == null ? "" : namespace + ".") : id + ".") + key;
			if (complex) {
				DefinedStructure structure = new DefinedStructure();
				structure.setNamespace(namespace);
				structure.setName(key);
				structure.setId(typeId);
				structure.setSuperType(superType);
				registry.register(structure);
				types.put(key, structure);
			}
			else {
				MDDSimpleType simpleType = new MDDSimpleType(typeId, namespace, key);
				simpleType.setSuperType(superType);
				registry.register(simpleType);
				types.put(key, simpleType);
			}
		}
		
		// the actual parsing pass
		for (String key : map.keySet()) {
			if (key.equals(META_KEY)) {
				continue;
			}
			// we can already assume it is a map (see error above)
			Map<String, Object> object = (Map<String, Object>) map.get(key);
			
			// let's parse it!
			if (types.get(key) instanceof ComplexType) {
				parseComplexType(registry, (Structure) types.get(key), object);
			}
			else {
				parseSimpleType(registry, (MDDSimpleType) types.get(key), object);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void parseSimpleType(TypeRegistry registry, MDDSimpleType simpleType, Map<String, Object> properties) throws MDDSchemaException {
		Map<String, Object> metadata = (Map<String, Object>) properties.get(META_KEY);
		if (metadata != null) {
			simpleType.setProperty(mapProperties(metadata, simpleType.getInstanceClass()));
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void parseComplexType(TypeRegistry registry, Structure structure, Map<String, Object> properties) throws MDDSchemaException {
		Map<String, Object> metadata = (Map<String, Object>) properties.get(META_KEY);
		if (metadata != null) {
			structure.setProperty(mapProperties(metadata));
		}
		for (String key : properties.keySet()) {
			if (key.equals(META_KEY)) {
				continue;
			}
			Type elementType;
			// everything else is a child!
			// it must refer to a type, we need to get that type to see if we want a simple or complex element
			Object object = properties.get(key);
			Map<String, Object> elementMetadata = null;
			// in the short hand, we just say:
			// author: string
			// but in most cases it is an object
			if (object instanceof String) {
				elementType = lookup(registry, (String) object);
			}
			else if (object instanceof Map) {
				Map<String, Object> keyMap = (Map<String, Object>) object;
				elementMetadata = (Map<String, Object>) keyMap.get(META_KEY);
				Object type = elementMetadata.get("type");
				if (type != null) {
					if (!(type instanceof String)) {
						throw new MDDSchemaException("Type is not a string: " + type);
					}
					elementType = lookup(registry, (String) type);
				}
				else {
					// anonymous type
					Structure anonymousType = new Structure();
					anonymousType.setName(key);
					parseComplexType(registry, anonymousType, keyMap);
					elementType = anonymousType;
				}
			}
			else {
				throw new MDDSchemaException("Unsupported field: " + key + " = " + object);
			}
			if (elementType == null) {
				throw new MDDSchemaException("Could not resolve element type for: " + key);
			}
			ModifiableElement<?> element;
			if (elementType instanceof SimpleType) {
				element = new SimpleElementImpl(key, (SimpleType) elementType, structure);
			}
			else {
				element = new ComplexElementImpl(key, (ComplexType) elementType, structure);
			}
			if (elementMetadata != null) {
				element.setProperty(mapProperties(elementMetadata));
			}
			structure.add(element);
		}
	}
	
	private Value<?>[] mapProperties(Map<String, Object> meta) throws MDDSchemaException {
		return mapProperties(meta, null);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Value<?>[] mapProperties(Map<String, Object> meta, Class<?> targetClass) throws MDDSchemaException {
		List<Value<?>> values = new ArrayList<Value<?>>();
		for (String key : meta.keySet()) {
			if (reservedProperties.contains(key)) {
				continue;
			}
			else if ("enumeration".equals(key)) {
				Object stringValues = meta.get(key);
				// if you have a single enumeration, make it a list for further processing
				if (stringValues instanceof String) {
					stringValues = Arrays.asList(stringValues);
				}
				EnumerationProperty enumerationProperty = new EnumerationProperty();
				List enumerationValues = new ArrayList();
				for (String enumeration : (List<String>) stringValues) {
					enumerationValues.add(ConverterFactory.getInstance().getConverter().convert(enumeration, targetClass));
				}
				values.add(new ValueImpl<List>(enumerationProperty, values));
			}
			else {
				Property<?> property = PropertyFactory.getInstance().getProperty(key);
				if (property == null) {
					throw new MDDSchemaException("Unknown property: " + key);
				}
				Object converted = String.class.equals(property.getValueClass()) 
					? meta.get(key) 
					: ConverterFactory.getInstance().getConverter().convert(meta.get(key), property.getValueClass());
				if (converted == null) {
					throw new MDDSchemaException("Can not parse the value for the property: " + key + " = " + meta.get(key));
				}
				values.add(new ValueImpl(property, converted));
			}
		}
		return values.toArray(new Value[values.size()]);
	}
	
	private SimpleType<?> getNativeType(String typeName) {
		if (typeName.equalsIgnoreCase("string")) {
			return wrapper.wrap(String.class);
		}
		else if (typeName.equalsIgnoreCase("boolean")) {
			return wrapper.wrap(Boolean.class);
		}
		else if (typeName.equalsIgnoreCase("integer")) {
			return wrapper.wrap(BigInteger.class);
		}
		else if (typeName.equalsIgnoreCase("long")) {
			return wrapper.wrap(Long.class);
		}
		else if (typeName.equalsIgnoreCase("int")) {
			return wrapper.wrap(Integer.class);
		}
		else if (typeName.equalsIgnoreCase("short")) {
			return wrapper.wrap(Short.class);
		}
		else if (typeName.equalsIgnoreCase("byte")) {
			return wrapper.wrap(Byte.class);
		}
		else if (typeName.equalsIgnoreCase("decimal")) {
			return wrapper.wrap(BigDecimal.class);
		}
		else if (typeName.equalsIgnoreCase("float")) {
			return wrapper.wrap(Float.class);
		}
		else if (typeName.equalsIgnoreCase("double")) {
			return wrapper.wrap(Double.class);
		}
		else if (typeName.equalsIgnoreCase("uri")) {
			return wrapper.wrap(URI.class);
		}
		else if (typeName.equalsIgnoreCase("uuid")) {
			return wrapper.wrap(UUID.class);
		}
		else if (typeName.equalsIgnoreCase("date") || typeName.equalsIgnoreCase("dateTime") || typeName.equalsIgnoreCase("time") || typeName.equalsIgnoreCase("year")) {
			return wrapper.wrap(Date.class);
		}
		else if (typeName.equalsIgnoreCase("binary")) {
			return wrapper.wrap(byte[].class);
		}
		else if (typeName.equalsIgnoreCase("duration")) {
			return wrapper.wrap(Duration.class);
		}
		return null;
	}
	
	public TypeRegistry parseGlobal() throws MDDSchemaException, MDDSyntaxException {
		try {
			String schema = new URLSchemaResolver().getSchema(new URI("classpath:/global.mdd"));
			MDDSchemaParser nonGlobalParser = new MDDSchemaParser();
			nonGlobalParser.setIncludeDefaultGlobalNamespace(false);
			return nonGlobalParser.parse(schema);
		}
		catch (URISyntaxException e) {
			throw new MDDSchemaException("Can not parse global namespace", e);
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
