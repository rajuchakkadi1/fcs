package com.fulfilment.application.monolith.stores;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;

@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

	@Inject
	LegacyStoreManagerGateway legacyStoreManagerGateway;
	@Inject
	TransactionSynchronizationRegistry transactionRegistry;

	private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());

	@GET
	public List<Store> get() {
		return Store.listAll(Sort.by("name"));
	}

	@GET
	@Path("{id}")
	public Store getSingle(@PathParam("id") Long id) {
		Store entity = Store.findById(id);
		if (entity == null) {
			throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
		}
		return entity;
	}

	@POST
	@Transactional
	public Response create(Store store) {
		if (store.id != null) {
			throw new WebApplicationException("Id was invalidly set on request.", 422);
		}

		store.persist();

		// Register post-commit hook so legacy integration runs only on DB commit success
		registerPostCommitLegacySync(() -> legacyStoreManagerGateway.createStoreOnLegacySystem(store));

		return Response.ok(store).status(201).build();
	}

	@PUT
	@Path("{id}")
	@Transactional
	public Store update(@PathParam("id") Long id, Store updatedStore) {
		if (updatedStore.name == null) {
			throw new WebApplicationException("Store Name was not set on request.", 422);
		}

		Store entity = Store.findById(id);
		if (entity == null) {
			throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
		}

		entity.name = updatedStore.name;
		entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

		registerPostCommitLegacySync(() -> legacyStoreManagerGateway.updateStoreOnLegacySystem(entity));

		return entity;
	}

	@PATCH
	@Path("{id}")
	@Transactional
	public Store patch(@PathParam("id") Long id, Store updatedStore) {
		Store entity = Store.findById(id);
		if (entity == null) {
			throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
		}

		// Inspect input payload fields rather than target entity state
		if (updatedStore.name != null) {
			entity.name = updatedStore.name;
		}

		if (updatedStore.quantityProductsInStock != 0) {
			entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
		}

		registerPostCommitLegacySync(() -> legacyStoreManagerGateway.updateStoreOnLegacySystem(entity));

		return entity;
	}

	@DELETE
	@Path("{id}")
	@Transactional
	public Response delete(@PathParam("id") Long id) {
		Store entity = Store.findById(id);
		if (entity == null) {
			throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
		}
		entity.delete();
		return Response.status(204).build();
	}

	/**
	 * Helper method to execute legacy gateway invocations safely after JTA commit.
	 */
	private void registerPostCommitLegacySync(Runnable action) {
		transactionRegistry.registerInterposedSynchronization(new Synchronization() {
			@Override
			public void beforeCompletion() {
			}

			@Override
			public void afterCompletion(int status) {
				if (status == Status.STATUS_COMMITTED) {
					try {
						action.run();
					} catch (Exception e) {
						LOGGER.error("Failed to synchronize store with legacy system", e);
					}
				}
			}
		});
	}
}