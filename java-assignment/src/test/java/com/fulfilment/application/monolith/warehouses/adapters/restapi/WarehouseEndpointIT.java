package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WarehouseEndpointIT {

  @Inject CreateWarehouseOperation createWarehouseOperation;

  @Inject ArchiveWarehouseOperation archiveWarehouseOperation;

  @Inject ReplaceWarehouseOperation replaceWarehouseOperation;

  @Inject WarehouseStore warehouseStore;

  @Inject UserTransaction userTransaction;

  @Test
  void getsSeedWarehouseByBusinessUnitCode() {
    given()
        .when()
        .get("/warehouse/MWH.001")
        .then()
        .statusCode(200)
        .body(
            "businessUnitCode", equalTo("MWH.001"),
            "location", equalTo("ZWOLLE-001"),
            "id", notNullValue());
  }

  @Test
  void replacesWarehouseAndArchivesPreviousActiveRecord() {
    String businessUnitCode = "WH-IT-" + UUID.randomUUID();
    String warehousePath = "/warehouse/" + businessUnitCode;

    given()
        .contentType("application/json")
        .body(warehousePayload(businessUnitCode, "EINDHOVEN-001", 30, 5))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo(businessUnitCode));

    given()
        .contentType("application/json")
        .body(warehousePayload(businessUnitCode, "EINDHOVEN-001", 40, 5))
        .when()
        .post(warehousePath + "/replacement")
        .then()
        .statusCode(200)
        .body(
            "businessUnitCode", equalTo(businessUnitCode),
            "capacity", equalTo(40),
            "stock", equalTo(5));

    given()
        .when()
        .get("/warehouse")
        .then()
        .statusCode(200)
        .body("findAll { it.businessUnitCode == '" + businessUnitCode + "' }.size()", equalTo(1));
  }

  @Test
  void rejectsDuplicateWarehouseBusinessUnitCode() {
    given()
        .contentType("application/json")
        .body(warehousePayload("MWH.001", "ZWOLLE-001", 20, 5))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsInvalidWarehouseLocationAndCapacityRules() {
    given()
        .contentType("application/json")
        .body(warehousePayload("WH-IT-INVALID-LOCATION", "UNKNOWN-LOCATION", 10, 1))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body(warehousePayload("WH-IT-TOO-MANY", "ZWOLLE-001", 10, 1))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body(warehousePayload("WH-IT-TOO-MUCH-CAPACITY", "ZWOLLE-002", 51, 1))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body(warehousePayload("WH-IT-TOO-MUCH-STOCK", "EINDHOVEN-001", 10, 11))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsInvalidWarehouseReplacementRequests() {
    given()
        .contentType("application/json")
        .body(warehousePayload("MWH.001", "ZWOLLE-001", 20, 5))
        .when()
        .post("/warehouse/MWH.001/replacement")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body(warehousePayload("WH-IT-MISSING", "EINDHOVEN-001", 20, 5))
        .when()
        .post("/warehouse/WH-IT-MISSING/replacement")
        .then()
        .statusCode(404);
  }

  @Test
  void replacementChangesAreRolledBackTogether() throws Exception {
    String businessUnitCode = "MWH.001";
    Warehouse activeWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    assertNotNull(activeWarehouse);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = businessUnitCode;
    replacement.location = activeWarehouse.location;
    replacement.capacity = activeWarehouse.capacity;
    replacement.stock = activeWarehouse.stock;
    replacement.createdAt = LocalDateTime.now();

    userTransaction.begin();
    try {
      activeWarehouse.archivedAt = LocalDateTime.now();
      replaceWarehouseOperation.replace(activeWarehouse, replacement);
    } finally {
      userTransaction.rollback();
    }

    Warehouse stillActive = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    assertNotNull(stillActive);
    assertEquals(activeWarehouse.capacity, stillActive.capacity);
    assertEquals(activeWarehouse.stock, stillActive.stock);
    assertEquals(
        1,
        warehouseStore.getAll().stream()
            .filter(warehouse -> businessUnitCode.equals(warehouse.businessUnitCode))
            .count());
  }

  @Test
  void createUseCasePersistsWarehouseAndRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> createWarehouseOperation.create(null));

    String businessUnitCode = "WH-IT-CREATE-" + UUID.randomUUID();
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = "EINDHOVEN-001";
    warehouse.capacity = 10;
    warehouse.stock = 2;
    warehouse.createdAt = LocalDateTime.now();

    try {
      createWarehouseOperation.create(warehouse);
      Warehouse persisted = warehouseStore.findByBusinessUnitCode(businessUnitCode);
      assertNotNull(persisted);
      assertEquals(10, persisted.capacity);
      assertEquals(2, persisted.stock);
    } finally {
      warehouseStore.remove(warehouse);
    }
  }

  @Test
  void archiveUseCaseArchivesPersistedWarehouseAndRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> archiveWarehouseOperation.archive(null));

    String businessUnitCode = "WH-IT-ARCHIVE-" + UUID.randomUUID();
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = "EINDHOVEN-001";
    warehouse.capacity = 10;
    warehouse.stock = 2;
    warehouse.createdAt = LocalDateTime.now();

    try {
      warehouseStore.create(warehouse);
      Warehouse activeWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
      assertNotNull(activeWarehouse);
      activeWarehouse.archivedAt = LocalDateTime.now();

      archiveWarehouseOperation.archive(activeWarehouse);

      org.junit.jupiter.api.Assertions.assertNull(
          warehouseStore.findByBusinessUnitCode(businessUnitCode));
    } finally {
      warehouseStore.remove(warehouse);
    }
  }

  @Test
  void replaceUseCasePersistsReplacementAndRejectsNullArguments() {
    assertThrows(
        IllegalArgumentException.class,
        () -> replaceWarehouseOperation.replace(null, new Warehouse()));
    assertThrows(
        IllegalArgumentException.class,
        () -> replaceWarehouseOperation.replace(new Warehouse(), null));

    String businessUnitCode = "WH-IT-REPLACE-" + UUID.randomUUID();
    Warehouse original = new Warehouse();
    original.businessUnitCode = businessUnitCode;
    original.location = "EINDHOVEN-001";
    original.capacity = 10;
    original.stock = 2;
    original.createdAt = LocalDateTime.now();

    try {
      warehouseStore.create(original);
      Warehouse activeWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
      assertNotNull(activeWarehouse);
      activeWarehouse.archivedAt = LocalDateTime.now();

      Warehouse replacement = new Warehouse();
      replacement.businessUnitCode = businessUnitCode;
      replacement.location = "EINDHOVEN-001";
      replacement.capacity = 15;
      replacement.stock = 2;
      replacement.createdAt = LocalDateTime.now();

      replaceWarehouseOperation.replace(activeWarehouse, replacement);

      Warehouse persisted = warehouseStore.findByBusinessUnitCode(businessUnitCode);
      assertNotNull(persisted);
      assertEquals(15, persisted.capacity);
      assertEquals(2, persisted.stock);
      assertEquals(
          1,
          warehouseStore.getAll().stream()
              .filter(item -> businessUnitCode.equals(item.businessUnitCode))
              .count());
    } finally {
      warehouseStore.remove(original);
    }
  }

  private String warehousePayload(String businessUnitCode, String location, int capacity, int stock) {
    return """
        {
          "businessUnitCode": "%s",
          "location": "%s",
          "capacity": %d,
          "stock": %d
        }
        """.formatted(businessUnitCode, location, capacity, stock);
  }
}
