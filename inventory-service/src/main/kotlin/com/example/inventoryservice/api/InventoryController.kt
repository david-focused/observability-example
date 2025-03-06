package com.example.inventoryservice.api

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("inventory")
class InventoryController(private val tracer: Tracer) {
    private val logger = LoggerFactory.getLogger(InventoryController::class.java)

    @PostMapping("check")
    fun checkInventory(@RequestBody request: CheckInventoryRequest): ResponseEntity<InventoryResponse> {
        logger.info("Checking inventory for product: ${request.productId}")
        
        // Create a custom span for inventory lookup
        val span = tracer.spanBuilder("inventory-lookup")
            .setAttribute("product.id", request.productId)
            .startSpan()

        // Detect special product IDs for error and delay simulation
        val isErrorOrder = request.productId.endsWith("-with-error")
        val isDelayOrder = request.productId.endsWith("-with-delay")
        
        try {
            span.addEvent("Starting inventory database lookup")
            
            // Check if this is a special product ID that should trigger an exception
            if (isErrorOrder) {
                logger.error("Detected error-triggering product ID: ${request.productId}")
                throw RuntimeException("Intentional inventory error for product: ${request.productId}")
            }
            
            // Check if this is a special product ID that should trigger a delay
            if (isDelayOrder) {
                logger.info("Detected delay-triggering product ID: ${request.productId}")
                // Sleep for 1 second
                Thread.sleep(1000)
                logger.info("Delay complete for product: ${request.productId}")
            }
            
            // hard-coded for demonstration purposes
            val available = true
            val quantity = 100

            // logging approach
            logger.info("Found ${quantity} units of product ${request.productId}")
            
            // Add inventory details to the span for observability
            span.setAttribute("inventory.available", available)
            span.setAttribute("inventory.quantity", quantity.toLong())
            span.addEvent("Product available")
            
            return ResponseEntity.ok(InventoryResponse(available, quantity))
        } catch (e: Exception) {
            logger.error("Error during inventory check", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Error checking inventory: ${e.message}")
            throw e
        } finally {
            span.end()
        }
    }
    
    @PostMapping("reserve")
    fun reserveInventory(@RequestBody request: ReserveInventoryRequest): ResponseEntity<ReserveInventoryResponse> {
        logger.info("Reserving inventory for order: ${request.orderId}, product: ${request.productId}, quantity: ${request.quantity}")
        
        val span = tracer.spanBuilder("inventory-reservation")
            .setAttribute("order.id", request.orderId)
            .setAttribute("product.id", request.productId)
            .setAttribute("order.quantity", request.quantity.toLong())
            .startSpan()

        // Detect special product IDs for error and delay simulation
        val isErrorOrder = request.productId.endsWith("-with-error")
        val isDelayOrder = request.productId.endsWith("-with-delay")
        
        try {
            if (isErrorOrder) {
                throw RuntimeException("Intentional inventory reservation error for product: ${request.productId}")
            }
            
            if (isDelayOrder) {
                Thread.sleep(1000)
            }
            
            // hard-coded for now
            val remainingQuantity = 95
            
            logger.info("Reserved ${request.quantity} units of product ${request.productId}. Remaining: ${remainingQuantity}")
            span.setAttribute("inventory.remaining", remainingQuantity.toLong())
            span.addEvent("Inventory successfully reserved")
            
            return ResponseEntity.ok(ReserveInventoryResponse(
                success = true, 
                message = "Inventory reserved successfully", 
                remainingQuantity = remainingQuantity
            ))
        } catch (e: Exception) {
            logger.error("Error during inventory reservation", e)
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, "Error reserving inventory: ${e.message}")
            throw e
        } finally {
            span.end()
        }
    }
}

// Data classes
data class CheckInventoryRequest(val productId: String)
data class InventoryResponse(val available: Boolean, val quantity: Int)
data class ReserveInventoryRequest(val orderId: String, val productId: String, val quantity: Int)
data class ReserveInventoryResponse(val success: Boolean, val message: String, val remainingQuantity: Int) 