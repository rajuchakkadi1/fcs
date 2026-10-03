package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

	@Override
	public List<Warehouse> getAll() {
		return this.list("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
	}

	@Override
	@Transactional
	public void create(Warehouse warehouse) {
		var entity = new DbWarehouse();
		copyToEntity(warehouse, entity);
		persist(entity);
	}

	@Override
	@Transactional
	public void update(Warehouse warehouse) {
		DbWarehouse entity = find("businessUnitCode = ?1 and archivedAt is null", warehouse.businessUnitCode)
				.firstResult();
		if (entity == null) {
			throw new IllegalArgumentException(
					"Warehouse with business unit code " + warehouse.businessUnitCode + " does not exist");
		}

		copyToEntity(warehouse, entity);
	}

	@Override
	@Transactional
	public void remove(Warehouse warehouse) {
		delete("businessUnitCode", warehouse.businessUnitCode);
	}

	@Override
	public Warehouse findByBusinessUnitCode(String buCode) {
		DbWarehouse entity = find("businessUnitCode = ?1 and archivedAt is null", buCode).firstResult();
		return entity == null ? null : entity.toWarehouse();
	}

	private void copyToEntity(Warehouse warehouse, DbWarehouse entity) {
		entity.businessUnitCode = warehouse.businessUnitCode;
		entity.location = warehouse.location;
		entity.capacity = warehouse.capacity;
		entity.stock = warehouse.stock;
		entity.createdAt = warehouse.createdAt;
		entity.archivedAt = warehouse.archivedAt;
	}
}
