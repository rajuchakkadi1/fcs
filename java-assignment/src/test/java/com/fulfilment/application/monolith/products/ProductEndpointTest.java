package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProductEndpointTest {

  @Test
  public void testCrudProduct() {
    final String path = "product";

    // List all, should have all 3 products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("TONSTAD"), containsString("KALLAX"), containsString("BESTÅ"));

    // Delete the TONSTAD:
    given().when().delete(path + "/1").then().statusCode(204);

    // List all, TONSTAD should be missing now:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(not(containsString("TONSTAD")), containsString("KALLAX"), containsString("BESTÅ"));
  }

  @Test
  public void createsGetsUpdatesAndDeletesProduct() {
    String name = "product-" + UUID.randomUUID().toString().substring(0, 8);
    long id =
        given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"description\":\"original\",\"price\":12.5,\"stock\":4}")
            .when()
            .post("/product")
            .then()
            .statusCode(201)
            .body("name", equalTo(name), "stock", equalTo(4))
            .extract()
            .jsonPath()
            .getLong("id");

    given()
        .when()
        .get("/product/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(name), "description", equalTo("original"));

    String updatedName = name + "-updated";
    given()
        .contentType("application/json")
        .body("{\"name\":\"" + updatedName + "\",\"description\":\"updated\",\"price\":15.0,\"stock\":8}")
        .when()
        .put("/product/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(updatedName), "stock", equalTo(8));

    given()
        .when()
        .delete("/product/" + id)
        .then()
        .statusCode(204);
  }

  @Test
  public void returnsNotFoundForUnknownProduct() {
    given().when().get("/product/9223372036854775807").then().statusCode(404);
    given()
        .contentType("application/json")
        .body("{\"name\":\"missing\"}")
        .when()
        .put("/product/9223372036854775807")
        .then()
        .statusCode(404);
    given().when().delete("/product/9223372036854775807").then().statusCode(404);
  }

  @Test
  public void rejectsCreateWithClientSuppliedIdAndUpdateWithoutName() {
    given()
        .contentType("application/json")
        .body("{\"id\":1,\"name\":\"invalid\"}")
        .when()
        .post("/product")
        .then()
        .statusCode(422);

    given()
        .contentType("application/json")
        .body("{\"description\":\"missing name\"}")
        .when()
        .put("/product/1")
        .then()
        .statusCode(422);
  }
}
