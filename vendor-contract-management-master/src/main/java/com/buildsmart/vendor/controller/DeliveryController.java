package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.DeliveryResponse;
import com.buildsmart.vendor.dto.request.DeliveryRequest;
import com.buildsmart.vendor.enums.DeliveryStatus;
import com.buildsmart.vendor.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Delivery", description = "Delivery management APIs")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @Operation(summary = "Get all deliveries", description = "Retrieves a paginated list of all deliveries")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved all deliveries")
    @GetMapping
    public Page<DeliveryResponse> getAllDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "deliveryId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.getAllDeliveries(pageable);
    }

    @Operation(summary = "Get delivery by ID", description = "Retrieves a delivery by its unique ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery found"),
            @ApiResponse(responseCode = "400", description = "Invalid delivery ID format"),
            @ApiResponse(responseCode = "404", description = "Delivery not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getDeliveryById(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DELIVERY, id, "deliveryId");
        DeliveryResponse delivery = deliveryService.getDeliveryById(id);
        if (delivery == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(delivery);
    }

    @Operation(summary = "Get deliveries by contract ID", description = "Retrieves deliveries for a specific contract")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    @GetMapping("/contract/{contractId}")
    public List<DeliveryResponse> getDeliveriesByContractId(@PathVariable String contractId) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, contractId);
        return deliveryService.getDeliveriesByContractId(contractId);
    }

    @Operation(summary = "Get deliveries by status", description = "Retrieves deliveries filtered by status")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    @GetMapping("/status/{status}")
    public List<DeliveryResponse> getDeliveriesByStatus(@PathVariable DeliveryStatus status) {
        return deliveryService.getDeliveriesByStatus(status);
    }

    @Operation(summary = "Create a new delivery", description = "Creates a new delivery record")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public DeliveryResponse createDelivery(@RequestBody DeliveryRequest request) {
        return deliveryService.createDelivery(request);
    }

    @Operation(summary = "Update a delivery", description = "Updates an existing delivery by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Delivery not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<DeliveryResponse> updateDelivery(@PathVariable String id, @RequestBody DeliveryRequest request) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DELIVERY, id, "deliveryId");
        DeliveryResponse updated = deliveryService.updateDelivery(id, request);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete a delivery", description = "Deletes a delivery by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Delivery not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteDelivery(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DELIVERY, id, "deliveryId");
        deliveryService.deleteDelivery(id);
        return ResponseEntity.ok().build();
    }
}
