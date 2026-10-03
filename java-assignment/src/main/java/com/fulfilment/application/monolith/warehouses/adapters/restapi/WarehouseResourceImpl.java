package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

	@Inject
	private WarehouseStore warehouseStore;

	@Inject
	private CreateWarehouseOperation createWarehouseOperation;

	@Inject
	private ArchiveWarehouseOperation archiveWarehouseOperation;

	@Inject
	private ReplaceWarehouseOperation replaceWarehouseOperation;

	@Inject
	private LocationResolver locationResolver;

	@Override
	public List<Warehouse> listAllWarehousesUnits() {
		return warehouseStore.getAll().stream().map(this::toWarehouseResponse).toList();
	}

	@Override
	public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
		validateWarehousePayload(data);

		var businessUnitCode = data.getBusinessUnitCode();
		var existingWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
		if (existingWarehouse != null) {
			throw new WebApplicationException(
					"Warehouse with business unit code " + businessUnitCode + " already exists.", 400);
		}

		var location = resolveLocation(data.getLocation());
		validateWarehouseCreationFeasibility(data, location);

		var warehouse = toDomain(data);
		warehouse.createdAt = LocalDateTime.now();
		warehouse.archivedAt = null;
		createWarehouseOperation.create(warehouse);

		return toWarehouseResponse(warehouseStore.findByBusinessUnitCode(businessUnitCode));
	}

	@Override
	public Warehouse getAWarehouseUnitByBusinessUnitCode(String businessUnitCode) {
		var warehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
		if (warehouse == null) {
			throw new WebApplicationException(
					"Warehouse with business unit code " + businessUnitCode + " does not exist.", 404);
		}
		return toWarehouseResponse(warehouse);
	}

	@Override
	public void archiveAWarehouseUnitByBusinessUnitCode(String businessUnitCode) {
		var warehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
		if (warehouse == null) {
			throw new WebApplicationException(
					"Warehouse with business unit code " + businessUnitCode + " does not exist.", 404);
		}

		warehouse.archivedAt = LocalDateTime.now();
		archiveWarehouseOperation.archive(warehouse);
	}

	@Override
	public Warehouse replaceTheCurrentActiveWarehouse(String businessUnitCode, @NotNull Warehouse data) {
		var normalizedBusinessUnitCode = businessUnitCode == null ? null : businessUnitCode.trim();
		if (normalizedBusinessUnitCode == null || normalizedBusinessUnitCode.isBlank()) {
			throw new WebApplicationException("Business unit code was not set on request.", 400);
		}

		var currentWarehouse = warehouseStore.findByBusinessUnitCode(normalizedBusinessUnitCode);
		if (currentWarehouse == null) {
			throw new WebApplicationException("Warehouse with business unit code " + normalizedBusinessUnitCode + " does not exist.", 404);
		}

		validateWarehousePayload(data);

		var replacementLocation = resolveLocation(data.getLocation());
		validateReplacementFeasibility(currentWarehouse, data, replacementLocation);

		currentWarehouse.archivedAt = LocalDateTime.now();

		var replacement = toDomain(data);
		replacement.createdAt = LocalDateTime.now();
		replacement.archivedAt = null;
		replaceWarehouseOperation.replace(currentWarehouse, replacement);

		return toWarehouseResponse(warehouseStore.findByBusinessUnitCode(normalizedBusinessUnitCode));
	}

	private void validateWarehousePayload(Warehouse data) {
		if (data == null) {
			throw new WebApplicationException("Warehouse payload is required.", 400);
		}
		if (data.getBusinessUnitCode() == null || data.getBusinessUnitCode().isBlank()) {
			throw new WebApplicationException("Business unit code was not set on request.", 400);
		}
		if (data.getLocation() == null || data.getLocation().isBlank()) {
			throw new WebApplicationException("Warehouse location was not set on request.", 400);
		}
		if (data.getCapacity() == null || data.getCapacity() < 0) {
			throw new WebApplicationException("Warehouse capacity was not set or is invalid.", 400);
		}
		if (data.getStock() == null || data.getStock() < 0) {
			throw new WebApplicationException("Warehouse stock was not set or is invalid.", 400);
		}
	}

	private void validateWarehouseCreationFeasibility(Warehouse data, Location location) {
		int totalCapacityAtLocation = warehouseStore.getAll().stream().filter(warehouse -> warehouse.location != null)
				.filter(warehouse -> warehouse.location.equals(location.identification))
				.mapToInt(warehouse -> warehouse.capacity).sum();

		long activeWarehousesAtLocation = warehouseStore.getAll().stream()
				.filter(warehouse -> warehouse.location != null)
				.filter(warehouse -> warehouse.location.equals(location.identification)).count();

		if (activeWarehousesAtLocation >= location.maxNumberOfWarehouses) {
			throw new WebApplicationException(
					"The maximum number of warehouses for location " + location.identification + " has been reached.",
					400);
		}

		if (totalCapacityAtLocation + data.getCapacity() > location.maxCapacity) {
			throw new WebApplicationException(
					"Warehouse capacity exceeds the maximum capacity for location " + location.identification + ".",
					400);
		}

		if (data.getStock() > data.getCapacity()) {
			throw new WebApplicationException("Warehouse stock exceeds the warehouse capacity.", 400);
		}
	}

	private void validateReplacementFeasibility(
			com.fulfilment.application.monolith.warehouses.domain.models.Warehouse currentWarehouse,
			Warehouse replacementData, Location location) {

		int newStock = replacementData.getStock() != null ? replacementData.getStock() : 0;
		int newCapacity = replacementData.getCapacity() != null ? replacementData.getCapacity() : 0;
		int currentStock = currentWarehouse.stock != null ? currentWarehouse.stock : 0;

		if (newStock > newCapacity) {
			throw new WebApplicationException("Warehouse stock exceeds the warehouse capacity.", 400);
		}

		if (newCapacity < currentStock) {
			throw new WebApplicationException(
					"Replacement warehouse capacity cannot accommodate the stock of the warehouse being replaced.",
					400);
		}

		if (newStock != currentStock) {
			throw new WebApplicationException(
					"Replacement warehouse stock must match the stock of the warehouse being replaced.", 400);
		}

		List<com.fulfilment.application.monolith.warehouses.domain.models.Warehouse> activeLocationWarehouses = warehouseStore
				.getAll().stream().filter(w -> w.archivedAt == null) // Exclude archived warehouse
				.filter(w -> location.identification != null && location.identification.equals(w.location))
				.filter(w -> !Objects.equals(w.businessUnitCode, currentWarehouse.businessUnitCode)).toList();

		int activeWarehousesCount = activeLocationWarehouses.size();
		if (activeWarehousesCount + 1 > location.maxNumberOfWarehouses) {
			throw new WebApplicationException(
					"The maximum number of warehouses for location " + location.identification + " has been reached.",
					400);
		}

		int totalLocationCapacity = activeLocationWarehouses.stream().mapToInt(w -> w.capacity != null ? w.capacity : 0)
				.sum();

		if (totalLocationCapacity + newCapacity > location.maxCapacity) {
			throw new WebApplicationException(
					"Warehouse capacity exceeds the maximum capacity for location " + location.identification + ".",
					400);
		}
	}

	private Location resolveLocation(String locationIdentifier) {
		try {
			return locationResolver.resolveByIdentifier(locationIdentifier);
		} catch (IllegalArgumentException exception) {
			throw new WebApplicationException("Warehouse location is invalid: " + locationIdentifier, 400);
		}
	}

	private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomain(Warehouse data) {
		var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
		warehouse.businessUnitCode = data.getBusinessUnitCode();
		warehouse.location = data.getLocation();
		warehouse.capacity = data.getCapacity();
		warehouse.stock = data.getStock();
		warehouse.createdAt = LocalDateTime.now();
		warehouse.archivedAt = null;
		return warehouse;
	}

	private Warehouse toWarehouseResponse(
			com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
		var response = new Warehouse();
		response.setId(warehouse.id == null ? null : String.valueOf(warehouse.id));
		response.setBusinessUnitCode(warehouse.businessUnitCode);
		response.setLocation(warehouse.location);
		response.setCapacity(warehouse.capacity);
		response.setStock(warehouse.stock);

		return response;
	}
}
