package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DbWarehouseTest {

  @Test
  void toWarehouseCopiesAllPersistedFields() {
    var createdAt = LocalDateTime.of(2024, 7, 1, 12, 30);
    var archivedAt = LocalDateTime.of(2025, 1, 2, 8, 15);
    var entity = new DbWarehouse();
    entity.id = 456L;
    entity.businessUnitCode = "MWH.001";
    entity.location = "ZWOLLE-001";
    entity.capacity = 100;
    entity.stock = 50;
    entity.createdAt = createdAt;
    entity.archivedAt = archivedAt;

    var warehouse = entity.toWarehouse();

    assertEquals(entity.id, warehouse.id);
    assertEquals(entity.businessUnitCode, warehouse.businessUnitCode);
    assertEquals(entity.location, warehouse.location);
    assertEquals(entity.capacity, warehouse.capacity);
    assertEquals(entity.stock, warehouse.stock);
    assertEquals(createdAt, warehouse.createdAt);
    assertEquals(archivedAt, warehouse.archivedAt);
  }

  @Test
  void toWarehousePreservesNullArchivedAtForActiveWarehouse() {
    var entity = new DbWarehouse();
    entity.businessUnitCode = "MWH.001";

    var warehouse = entity.toWarehouse();

    assertEquals("MWH.001", warehouse.businessUnitCode);
    assertNull(warehouse.archivedAt);
  }
}
