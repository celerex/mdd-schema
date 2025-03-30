package be.celerex.mdd.schema.core;

public class MDDSchemaException extends Exception {

	private static final long serialVersionUID = 1L;

	public MDDSchemaException() {
		super();
	}

	public MDDSchemaException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public MDDSchemaException(String message, Throwable cause) {
		super(message, cause);
	}

	public MDDSchemaException(String message) {
		super(message);
	}

	public MDDSchemaException(Throwable cause) {
		super(cause);
	}
	

}
