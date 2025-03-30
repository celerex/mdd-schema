package be.celerex.mdd.schema.api;

import java.net.URI;

import be.celerex.mdd.schema.core.MDDSchemaException;

public interface SchemaResolver {
	public String getSchema(URI uri) throws MDDSchemaException;
}
