package com.fulfilment.application.monolith.warehouses.domain.models;

import java.time.LocalDateTime;

public class WarehouseFulfillmentAssociation {

  public Long id;
  public Long productId;
  public Long storeId;
  public String warehouseBusinessUnitCode;
  public LocalDateTime createdAt;

  public WarehouseFulfillmentAssociation() {}

  public WarehouseFulfillmentAssociation(Long productId, Long storeId, String warehouseBusinessUnitCode) {
    this.productId = productId;
    this.storeId = storeId;
    this.warehouseBusinessUnitCode = warehouseBusinessUnitCode;
    this.createdAt = LocalDateTime.now();
  }
}
