package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StoreEndpointTest {

  @Test
  void createsGetsUpdatesPatchesAndDeletesStore() {
    String name = "store-" + UUID.randomUUID().toString().substring(0, 8);
    long id =
        given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"quantityProductsInStock\":3}")
            .when()
            .post("/store")
            .then()
            .statusCode(201)
            .body("name", equalTo(name), "quantityProductsInStock", equalTo(3))
            .extract()
            .jsonPath()
            .getLong("id");

    given()
        .when()
        .get("/store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(name));

    String updatedName = name + "-updated";
    given()
        .contentType("application/json")
        .body("{\"name\":\"" + updatedName + "\",\"quantityProductsInStock\":7}")
        .when()
        .put("/store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(updatedName), "quantityProductsInStock", equalTo(7));

    given()
        .contentType("application/json")
        .body("{\"quantityProductsInStock\":9}")
        .when()
        .patch("/store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(updatedName), "quantityProductsInStock", equalTo(9));

    given()
        .when()
        .delete("/store/" + id)
        .then()
        .statusCode(204);
  }

  @Test
  void rejectsInvalidRequestsAndReturnsNotFoundForMissingStore() {
    given()
        .contentType("application/json")
        .body("{\"id\":1,\"name\":\"client-id\"}")
        .when()
        .post("/store")
        .then()
        .statusCode(422);

    given()
        .contentType("application/json")
        .body("{\"quantityProductsInStock\":2}")
        .when()
        .put("/store/1")
        .then()
        .statusCode(422);

    given().when().get("/store/9223372036854775807").then().statusCode(404);
    given()
        .contentType("application/json")
        .body("{\"name\":\"missing\"}")
        .when()
        .put("/store/9223372036854775807")
        .then()
        .statusCode(404);
    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .patch("/store/9223372036854775807")
        .then()
        .statusCode(404);
    given().when().delete("/store/9223372036854775807").then().statusCode(404);
  }
}
