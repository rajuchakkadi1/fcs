package com.fulfilment.application.monolith.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class ErrorMapper implements ExceptionMapper<Exception> {

	private static final Logger LOGGER = Logger.getLogger(ErrorMapper.class);

	@Inject
	ObjectMapper objectMapper;

	@Override
	public Response toResponse(Exception exception) {
		LOGGER.error("Failed to handle request", exception);

		int code = 500;
		if (exception instanceof WebApplicationException) {
			code = ((WebApplicationException) exception).getResponse().getStatus();
		}

		ObjectNode exceptionJson = objectMapper.createObjectNode();
		exceptionJson.put("exceptionType", exception.getClass().getName());
		exceptionJson.put("code", code);

		if (exception.getMessage() != null) {
			exceptionJson.put("error", exception.getMessage());
		}

		return Response.status(code).entity(exceptionJson).build();
	}
}