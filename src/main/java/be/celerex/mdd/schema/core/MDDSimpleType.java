package be.celerex.mdd.schema.core;

import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.Unmarshallable;
import be.nabu.libs.types.base.BaseType;
import be.nabu.libs.validator.api.Validator;

public class MDDSimpleType<T> extends BaseType<T> implements SimpleType<T>, Marshallable<T>, Unmarshallable<T>, Artifact {

	private String namespace;
	private String name;
	private String id;

	MDDSimpleType(String id, String namespace, String name) {
		this.id = id;
		this.namespace = namespace;
		this.name = name;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Validator<T> createValidator(Value<?>...values) {
		Type type = this;
		while (type.getSuperType() != null) {
			type = type.getSuperType();
		}
		return (Validator<T>) type.createValidator(values);
	}

	@Override
	public String getName(Value<?>... values) {
		return name;
	}

	@Override
	public String getNamespace(Value<?>... values) {
		return namespace;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T unmarshal(String content, Value<?>... values) {
		return ((Unmarshallable<T>) getSuperType()).unmarshal(content, values);
	}

	@SuppressWarnings("unchecked")
	@Override
	public String marshal(T object, Value<?>... values) {
		return ((Marshallable<T>) getSuperType()).marshal(object, values);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Class<T> getInstanceClass() {
		Class clazz = getSuperType() == null ? String.class : ((SimpleType) getSuperType()).getInstanceClass();
		return (Class<T>) clazz;
	}

	@Override
	public String getId() {
		return id;
	}
	
	@Override
	public void setSuperType(Type superType) {
		super.setSuperType(superType);
	}
	
	@Override
	public String toString() {
		return (namespace == null ? "" : namespace + "#") + getName();
	}
}
