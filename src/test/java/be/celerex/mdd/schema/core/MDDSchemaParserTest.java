package be.celerex.mdd.schema.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import be.celerex.mdd.core.MDDSyntaxException;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.TypeRegistry;
import be.nabu.libs.types.binding.json.JSONBinding;
import junit.framework.TestCase;

public class MDDSchemaParserTest extends TestCase {
	
	public void testFiles() throws MDDSchemaException, MDDSyntaxException {
		String content = readFile("examples/test1.mdd");
		MDDSchemaParser parser = new MDDSchemaParser();
		TypeRegistry typeRegistry = parser.parse(content);
		ComplexContent company = typeRegistry.getComplexType("local.mdd-schema.test", "company").newInstance();
		company.set("name", "Test Company");
		company.set("vat", "BE0123456789");
		String jsonify = jsonify(company, false);
		
		assertEquals(
			"{\"name\": \"Test Company\", \"vat\": \"BE0123456789\"}",
			jsonify
		);
	}

	private String jsonify(ComplexContent company, boolean pretty) {
		try {
			JSONBinding binding = new JSONBinding(company.getType());
			binding.setPrettyPrint(pretty);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			binding.marshal(output, company);
			return new String(output.toByteArray());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String readFile(String file) {
		String content;
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(file)) {
			byte [] bytes = new byte[1024];
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			int read = 0;
			while ((read = input.read(bytes)) > 0) {
				output.write(bytes, 0, read);
			}
			content = new String(output.toByteArray());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return content;
	}
}
