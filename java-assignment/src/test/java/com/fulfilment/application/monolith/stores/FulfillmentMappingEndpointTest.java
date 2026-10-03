package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FulfillmentMappingEndpointTest {

  @Inject
  WarehouseRepository warehouseRepository;

  @Test
  void createsUniqueFulfillmentRoutesAndEnforcesProductWarehouseLimit() {
    String unique = UUID.randomUUID().toString().substring(0, 8);
    long storeId =
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"store-" + unique + "\"}")
            .when()
            .post("/store")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("id");
    long productId = createProduct("product-" + unique);

    String firstRoute = route(storeId, productId, 1);
    given()
        .contentType(ContentType.JSON)
        .body(route(storeId, productId, Long.MAX_VALUE))
        .when()
        .post("/store/fulfillment")
        .then()
        .statusCode(404);

    given().contentType(ContentType.JSON).body(firstRoute).when().post("/store/fulfillment").then()
        .statusCode(201)
        .body("storeId", equalTo((int) storeId))
        .body("productId", equalTo((int) productId))
        .body("warehouseId", equalTo(1));

    given().contentType(ContentType.JSON).body(firstRoute).when().post("/store/fulfillment").then()
        .statusCode(409);

    given()
        .contentType(ContentType.JSON)
        .body(route(storeId, productId, 2))
        .when()
        .post("/store/fulfillment")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body(route(storeId, productId, 3))
        .when()
        .post("/store/fulfillment")
        .then()
        .statusCode(422);

    for (int i = 0; i < 4; i++) {
      long additionalProductId = createProduct("product-" + unique + "-" + i);
      given()
          .contentType(ContentType.JSON)
          .body(route(storeId, additionalProductId, 1))
          .when()
          .post("/store/fulfillment")
          .then()
          .statusCode(201);
    }

    long sixthProductId = createProduct("product-" + unique + "-sixth");
    given()
        .contentType(ContentType.JSON)
        .body(route(storeId, sixthProductId, 1))
        .when()
        .post("/store/fulfillment")
        .then()
        .statusCode(422);

    for (int i = 0; i < 2; i++) {
      long additionalProductId = createProduct("store-route-product-" + unique + "-" + i);
      given()
          .contentType(ContentType.JSON)
          .body(route(storeId, additionalProductId, 3))
          .when()
          .post("/store/fulfillment")
          .then()
          .statusCode(201);
    }

    long fourthWarehouseId = createWarehouse("warehouse-" + unique);
    long finalProductId = createProduct("store-route-product-" + unique + "-final");
    given()
        .contentType(ContentType.JSON)
        .body(route(storeId, finalProductId, fourthWarehouseId))
        .when()
        .post("/store/fulfillment")
        .then()
        .statusCode(422);
  }

  private long createProduct(String name) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"" + name + "\"}")
        .when()
        .post("/product")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  private long createWarehouse(String businessUnitCode) {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"businessUnitCode\":\""
                + businessUnitCode
                + "\",\"location\":\"HELMOND-001\",\"capacity\":5,\"stock\":0}")
        .when()
        .post("/warehouse")
        .then()
        .statusCode(200);
    return warehouseRepository.find("businessUnitCode", businessUnitCode).firstResult().id;
  }

  private String route(long storeId, long productId, long warehouseId) {
    return "{\"storeId\":" + storeId + ",\"productId\":" + productId + ",\"warehouseId\":" + warehouseId + "}";
  }
}
