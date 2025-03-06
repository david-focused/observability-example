package com.example.inventoryservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class InventoryServiceApplication

fun main(args: Array<String>) {
    runApplication<InventoryServiceApplication>(*args)
} 