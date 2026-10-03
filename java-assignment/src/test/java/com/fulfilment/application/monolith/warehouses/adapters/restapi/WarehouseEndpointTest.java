package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WarehouseEndpointTest {

  @Test
  public void listReturnsActiveSeedWarehouses() {
    given()
        .when()
        .get("/warehouse")
        .then()
        .statusCode(200)
        .body(
            containsString("MWH.001"),
            containsString("MWH.012"),
            containsString("MWH.023"));
  }

  @Test
  public void createGetAndArchiveWarehouseByBusinessUnitCode() {
    String businessUnitCode = "WH-IT-AMSTERDAM";
    String warehousePath = "/warehouse/" + businessUnitCode;

    given()
        .contentType("application/json")
        .body(
            """
            {
              "businessUnitCode": "WH-IT-AMSTERDAM",
              "location": "AMSTERDAM-002",
              "capacity": 50,
              "stock": 5
            }
            """)
        .when()
        .post("/warehouse")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo(businessUnitCode), "id", notNullValue());

    given()
        .when()
        .get(warehousePath)
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo(businessUnitCode), "location", equalTo("AMSTERDAM-002"));

    given()
        .when()
        .delete(warehousePath)
        .then()
        .statusCode(204);

    given()
        .when()
        .get(warehousePath)
        .then()
        .statusCode(404);

    given()
        .when()
        .get("/warehouse")
        .then()
        .statusCode(200)
        .body(not(containsString(businessUnitCode)));
  }
}
