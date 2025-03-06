package com.example.shippingservice.api

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("shipments")
class ShippingController {
    private val logger = LoggerFactory.getLogger(ShippingController::class.java)

    @PostMapping("create")
    fun create(@RequestBody shipment: CreateShipmentRequest): ResponseEntity<String> {
        logger.info("Creating shipment for order: ${shipment.orderId}")
        return ResponseEntity.ok("Shipped!");
    }
}

data class CreateShipmentRequest(val orderId: String)