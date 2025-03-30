package be.celerex.mdd.schema.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

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
		
		company = typeRegistry.getComplexType("local.mdd-schema.test", "companyWithContracts").newInstance();
		ComplexContent contract = typeRegistry.getComplexType("local.mdd-schema.test", "contract").newInstance();
		contract.set("id", UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"));
		contract.set("signed", new Date(1743355417767l));
		company.set("contracts[0]", contract);
		company.set("name", "Test Company");
		company.set("vat", "BE0123456789");
		company.set("signatory", "bob");
		
		jsonify = jsonify(company, false);
		assertEquals(
			"{\"name\": \"Test Company\", \"vat\": \"BE0123456789\", \"signatory\": \"bob\", \"contracts\": [{\"id\": \"f81d4fae7dec11d0a76500a0c91e6bf6\", \"signed\": \"2025-03-30T19:23:37.767+02:00\"}]}",
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
