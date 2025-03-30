package be.celerex.mdd.schema.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;

import be.celerex.mdd.schema.api.SchemaResolver;

public class URLSchemaResolver implements SchemaResolver {

	private Charset charset = Charset.defaultCharset();
	
	@Override
	public String getSchema(URI uri) throws MDDSchemaException {
		try {
			// for classpaths we remove the leading / if necessary
			URL url = "classpath".equals(uri.getScheme()) ? Thread.currentThread().getContextClassLoader().getResource(uri.getPath().replaceFirst("^[/]", "")) : uri.toURL();
			try (InputStream input = url.openStream()) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				byte [] buffer = new byte[8096];
				int read = 0;
				while ((read = input.read(buffer)) > 0) {
					output.write(buffer, 0, read);
				}
				return new String(output.toByteArray(), charset);
			}
		}
		catch (Exception e) {
			throw new MDDSchemaException("Could not resolve: " + uri, e);
		}
	}
	public Charset getCharset() {
		return charset;
	}
	public void setCharset(Charset charset) {
		this.charset = charset;
	}
}
