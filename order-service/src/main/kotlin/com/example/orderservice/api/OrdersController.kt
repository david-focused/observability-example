package com.example.orderservice.api

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.*

@RestController
@RequestMapping("orders")
class OrdersController(
    private val restTemplate: RestTemplate,
    private val tracer: Tracer,
    @Value("\${shipping.service.url}") private val shippingServiceUrl: String,
    @Value("\${inventory.service.url}") private val inventoryServiceUrl: String
) {
    private val logger = LoggerFactory.getLogger(OrdersController::class.java)

    @PostMapping("create")
    fun create(@RequestBody order: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val orderId = UUID.randomUUID().toString()
        logger.info("Creating order $orderId for product ${order.productId} with quantity ${order.amount}")
        
        // Create a custom span for the order creation process
        val orderSpan = tracer.spanBuilder("order-creation-process")
            .setAttribute("order.id", orderId)
            .setAttribute("product.id", order.productId)
            .setAttribute("order.quantity", order.amount.toLong())
            .startSpan()
        
        try {
            orderSpan.addEvent("Starting inventory check")
            val inventoryCheckResult = checkInventory(orderId, order.productId, order.amount, orderSpan)
            
            if (!inventoryCheckResult.first) {
                logger.warn("Order $orderId failed during inventory check")
                orderSpan.setStatus(StatusCode.ERROR, "Inventory check failed")
                return ResponseEntity.ok(OrderResponse(
                    orderId = orderId,
                    status = "FAILED",
                    message = "Inventory check failed: ${inventoryCheckResult.second}"
                ))
            }
            
            orderSpan.addEvent("Starting inventory reservation")
            val inventoryReservationResult = reserveInventory(orderId, order.productId, order.amount, orderSpan)
            
            if (!inventoryReservationResult.first) {
                logger.warn("Order $orderId failed during inventory reservation")
                orderSpan.setStatus(StatusCode.ERROR, "Inventory reservation failed")
                return ResponseEntity.ok(OrderResponse(
                    orderId = orderId,
                    status = "FAILED",
                    message = "Inventory reservation failed: ${inventoryReservationResult.second}"
                ))
            }
            
            orderSpan.addEvent("Starting shipment creation")
            val shipmentResult = createShipment(orderId, orderSpan)
            
            if (!shipmentResult.first) {
                logger.warn("Order $orderId failed during shipment creation")
                orderSpan.setStatus(StatusCode.ERROR, "Shipment creation failed")
                return ResponseEntity.ok(OrderResponse(
                    orderId = orderId,
                    status = "FAILED",
                    message = "Shipment creation failed: ${shipmentResult.second}"
                ))
            }
            
            logger.info("Order $orderId successfully created")
            orderSpan.setStatus(StatusCode.OK)
            
            val createdOrderUri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(orderId)
                .toUri()
            
            return ResponseEntity
                .created(createdOrderUri)
                .body(OrderResponse(
                    orderId = orderId,
                    status = "CREATED",
                    message = "Order successfully created"
                ))
        } catch (e: Exception) {
            logger.error("Unexpected error creating order $orderId", e)
            orderSpan.recordException(e)
            orderSpan.setStatus(StatusCode.ERROR, "Unexpected error: ${e.message}")
            
            return ResponseEntity.ok(OrderResponse(
                orderId = orderId,
                status = "ERROR",
                message = "Unexpected error: ${e.message}"
            ))
        } finally {
            orderSpan.end()
        }
    }
    
    private fun checkInventory(orderId: String, productId: String, quantity: Int, parentSpan: Span): Pair<Boolean, String> {
        val span = tracer.spanBuilder("check-inventory")
            .setParent(Context.current().with(parentSpan))
            .setAttribute("order.id", orderId)
            .setAttribute("product.id", productId)
            .setAttribute("order.quantity", quantity.toLong())
            .startSpan()
        
        try {
            logger.debug("Checking inventory for product $productId")
            
            val request = mapOf("productId" to productId)
            
            val response = restTemplate.postForEntity(
                "$inventoryServiceUrl/inventory/check",
                request,
                InventoryResponse::class.java
            )
            
            val inventoryResponse = response.body
            if (inventoryResponse == null) {
                logger.warn("Received null response from inventory service")
                span.setStatus(StatusCode.ERROR, "Null response from inventory service")
                return Pair(false, "Invalid response from inventory service")
            }
            
            // Record inventory details in span for observability
            span.setAttribute("inventory.available", inventoryResponse.available)
            span.setAttribute("inventory.quantity", inventoryResponse.quantity.toLong())
            
            logger.info("Inventory check successful for product $productId")
            return Pair(true, "Inventory available")
        } catch (e: HttpServerErrorException) {
            logger.error("Server error checking inventory for product $productId", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Server error checking inventory: ${e.message}")
            return Pair(false, "Server error checking inventory: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error checking inventory for product $productId", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Error checking inventory: ${e.message}")
            return Pair(false, "Error checking inventory: ${e.message}")
        } finally {
            span.end()
        }
    }
    
    private fun reserveInventory(orderId: String, productId: String, quantity: Int, parentSpan: Span): Pair<Boolean, String> {
        val span = tracer.spanBuilder("reserve-inventory")
            .setParent(Context.current().with(parentSpan))
            .setAttribute("order.id", orderId)
            .setAttribute("product.id", productId)
            .setAttribute("order.quantity", quantity.toLong())
            .startSpan()
        
        try {
            logger.debug("Reserving inventory for order $orderId, product $productId, quantity $quantity")
            
            val request = mapOf(
                "orderId" to orderId,
                "productId" to productId,
                "quantity" to quantity
            )
            
            val response = restTemplate.postForEntity(
                "$inventoryServiceUrl/inventory/reserve",
                request,
                ReserveInventoryResponse::class.java
            )
            
            val reservationResponse = response.body
            if (reservationResponse == null) {
                logger.warn("Received null response from inventory reservation")
                span.setStatus(StatusCode.ERROR, "Null response from inventory service")
                return Pair(false, "Invalid response from inventory service")
            }
            
            // Record reservation details in span for observability
            span.setAttribute("reservation.success", reservationResponse.success)
            span.setAttribute("inventory.remaining", reservationResponse.remainingQuantity.toLong())
            
            logger.info("Successfully reserved inventory for order $orderId")
            return Pair(true, "Inventory reserved successfully")
        } catch (e: HttpServerErrorException) {
            logger.error("Server error reserving inventory for order $orderId", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Server error reserving inventory: ${e.message}")
            return Pair(false, "Server error reserving inventory: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error reserving inventory for order $orderId", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Error reserving inventory: ${e.message}")
            return Pair(false, "Error reserving inventory: ${e.message}")
        } finally {
            span.end()
        }
    }
    
    private fun createShipment(orderId: String, parentSpan: Span): Pair<Boolean, String> {
        val span = tracer.spanBuilder("create-shipment")
            .setParent(Context.current().with(parentSpan))
            .setAttribute("order.id", orderId)
            .startSpan()
        
        try {
            logger.debug("Creating shipment for order $orderId")
            
            val request = mapOf("orderId" to orderId)
            
            val response = restTemplate.postForEntity(
                "$shippingServiceUrl/shipments/create",
                request,
                String::class.java
            )
            
            logger.info("Shipment created for order $orderId: ${response.body}")
            return Pair(true, "Shipment created successfully")
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating shipment", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Server error creating shipment: ${e.message}")
            return Pair(false, "Server error creating shipment: ${e.message}")
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating shipment", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Client error creating shipment: ${e.message}")
            return Pair(false, "Client error creating shipment: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error creating shipment", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Unexpected error creating shipment: ${e.message}")
            return Pair(false, "Unexpected error creating shipment: ${e.message}")
        } finally {
            span.end()
        }
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<Optional<Order>> {
        logger.info("Retrieving order $orderId")
        return ResponseEntity.ok(Optional.empty())
    }
}

data class Order(val productId: String, val amount: Int)
data class CreateOrderRequest(val productId: String, val amount: Int)
data class OrderResponse(val orderId: String, val status: String, val message: String)
data class InventoryResponse(val available: Boolean, val quantity: Int)
data class ReserveInventoryResponse(val success: Boolean, val message: String, val remainingQuantity: Int)