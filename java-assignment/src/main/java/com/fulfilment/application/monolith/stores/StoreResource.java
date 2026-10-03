package com.fulfilment.application.monolith.stores;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
	@Inject
	ProductRepository productRepository;
	@Inject
	WarehouseRepository warehouseRepository;
	@Inject
	EntityManager entityManager;

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

	@POST
	@Path("fulfillment")
	@Transactional
	public Response createFulfillment(FulfillmentMappingRequest request) {
		if (request == null) {
			throw new WebApplicationException("Fulfillment request is required.", 422);
		}
		if (request.storeId == null || request.storeId <= 0) {
			throw new WebApplicationException("Store id is required.", 422);
		}
		if (request.productId == null || request.productId <= 0) {
			throw new WebApplicationException("Product id is required.", 422);
		}
		if (request.warehouseId == null || request.warehouseId <= 0) {
			throw new WebApplicationException("Warehouse id is required.", 422);
		}

		Store store = Store.findById(request.storeId);
		if (store == null) {
			throw new WebApplicationException("Store with id of " + request.storeId + " does not exist.", 404);
		}

		Product product = productRepository.findById(request.productId);
		if (product == null) {
			throw new WebApplicationException("Product with id of " + request.productId + " does not exist.", 404);
		}

		DbWarehouse warehouse = warehouseRepository.findById(request.warehouseId);
		if (warehouse == null) {
			throw new WebApplicationException("Warehouse with id of " + request.warehouseId + " does not exist.", 404);
		}

		entityManager.lock(store, LockModeType.PESSIMISTIC_WRITE);
		entityManager.lock(product, LockModeType.PESSIMISTIC_WRITE);
		entityManager.lock(warehouse, LockModeType.PESSIMISTIC_WRITE);

		if (FulfillmentMapping.count("store.id = ?1 and product.id = ?2 and warehouse.id = ?3", request.storeId,
				request.productId, request.warehouseId) > 0) {
			throw new WebApplicationException("Fulfillment mapping already exists for store " + request.storeId
					+ ", product " + request.productId + " and warehouse " + request.warehouseId + ".", 409);
		}

		validateProductStoreWarehouseLimits(store, product, warehouse);

		FulfillmentMapping mapping = new FulfillmentMapping();
		mapping.store = store;
		mapping.product = product;
		mapping.warehouse = warehouse;
		mapping.persist();

		FulfillmentMappingResponse response = new FulfillmentMappingResponse();
		response.id = mapping.id;
		response.storeId = store.id;
		response.productId = product.id;
		response.warehouseId = warehouse.id;
		return Response.status(201).entity(response).build();
	}

	private void validateProductStoreWarehouseLimits(Store store, Product product, DbWarehouse warehouse) {
		Set<Long> warehousesForProductInStore = new HashSet<>();
		for (Object object : FulfillmentMapping.list("store.id = ?1 and product.id = ?2", store.id, product.id)) {
			if (object instanceof FulfillmentMapping item) {
				warehousesForProductInStore.add(item.warehouse.id);
			}
		}
		if (warehousesForProductInStore.size() >= 2 && !warehousesForProductInStore.contains(warehouse.id)) {
			throw new WebApplicationException(
					"Each product can be fulfilled by a maximum of 2 different warehouses per store.", 422);
		}

		Set<Long> warehousesForStore = new HashSet<>();
		for (Object object : FulfillmentMapping.list("store.id = ?1", store.id)) {
			if (object instanceof FulfillmentMapping item) {
				warehousesForStore.add(item.warehouse.id);
			}
		}
		if (warehousesForStore.size() >= 3 && !warehousesForStore.contains(warehouse.id)) {
			throw new WebApplicationException("Each store can be fulfilled by a maximum of 3 different warehouses.", 422);
		}

		Set<Long> productsForWarehouse = new HashSet<>();
		for (Object object : FulfillmentMapping.list("warehouse.id = ?1", warehouse.id)) {
			if (object instanceof FulfillmentMapping item) {
				productsForWarehouse.add(item.product.id);
			}
		}
		if (productsForWarehouse.size() >= 5 && !productsForWarehouse.contains(product.id)) {
			throw new WebApplicationException("Each warehouse can store a maximum of 5 product types.", 422);
		}
	}

	public static class FulfillmentMappingResponse {
		public Long id;
		public Long storeId;
		public Long productId;
		public Long warehouseId;
	}

	public static class FulfillmentMappingRequest {
		public Long storeId;
		public Long productId;
		public Long warehouseId;
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