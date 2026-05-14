package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.DeliveryResponse;
import com.buildsmart.vendor.dto.request.DeliveryRequest;
import com.buildsmart.vendor.enums.DeliveryStatus;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.ContractService;
import com.buildsmart.vendor.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Delivery REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - VENDOR: sees only deliveries whose contractId belongs to their own contracts.
 *     ContractIds are resolved from their JWT vendorId via ContractService.
 *   - ADMIN / PROJECT_MANAGER: unrestricted access to all deliveries.
 */
@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Delivery", description = "Delivery management APIs")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private AuthenticatedUserResolver authenticatedUserResolver;

    // ── GET endpoints — vendor-scoped ────────────────────────────────────────

    @Operation(
            summary = "Get all deliveries (paginated)",
            description = "**VENDOR**: returns only deliveries for the vendor's own contracts.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all deliveries.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    @GetMapping
    public Page<DeliveryResponse> getAllDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "deliveryId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest httpRequest) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        if (isVendor()) {
            String vendorId = resolveVendorId(httpRequest);
            List<String> contractIds = contractService.getContractIdsByVendorId(vendorId);
            return deliveryService.getDeliveriesByContractIds(contractIds, pageable);
        }
        return deliveryService.getAllDeliveries(pageable);
    }

    @Operation(
            summary = "Get delivery by ID",
            description = "**VENDOR**: returns 403 if the delivery's contract does not belong to the calling vendor.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: unrestricted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery found"),
            @ApiResponse(responseCode = "400", description = "Invalid delivery ID format"),
            @ApiResponse(responseCode = "403", description = "Vendor accessing another vendor's delivery"),
            @ApiResponse(responseCode = "404", description = "Delivery not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getDeliveryById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.DELIVERY, id, "deliveryId");
        DeliveryResponse delivery = deliveryService.getDeliveryById(id);
        if (delivery == null) {
            return ResponseEntity.notFound().build();
        }
        if (isVendor()) {
            assertDeliveryOwnership(httpRequest, delivery.getContractId());
        }
        return ResponseEntity.ok(delivery);
    }

    @Operation(
            summary = "Get deliveries by contract ID",
            description = "**VENDOR**: returns 403 if the contract does not belong to the calling vendor.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: unrestricted.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    @GetMapping("/contract/{contractId}")
    public List<DeliveryResponse> getDeliveriesByContractId(
            @PathVariable String contractId,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, contractId);
        if (isVendor()) {
            assertDeliveryOwnership(httpRequest, contractId);
        }
        return deliveryService.getDeliveriesByContractId(contractId);
    }

    @Operation(
            summary = "Get deliveries by status",
            description = "**VENDOR**: returns only their own deliveries with the given status.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all deliveries with the given status.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    @GetMapping("/status/{status}")
    public List<DeliveryResponse> getDeliveriesByStatus(
            @PathVariable DeliveryStatus status,
            HttpServletRequest httpRequest) {

        if (isVendor()) {
            String vendorId = resolveVendorId(httpRequest);
            List<String> contractIds = contractService.getContractIdsByVendorId(vendorId);
            return deliveryService.getDeliveriesByContractIdsAndStatus(contractIds, status);
        }
        return deliveryService.getDeliveriesByStatus(status);
    }

    // ── Mutation endpoints (VENDOR role only) ────────────────────────────────

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
    public ResponseEntity<DeliveryResponse> updateDelivery(
            @PathVariable String id,
            @RequestBody DeliveryRequest request) {
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True when the current JWT holder has the VENDOR role. */
    private boolean isVendor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
    }

    /**
     * Resolves the authenticated vendor's IAM userId.
     * Throws 401 if the identity cannot be determined.
     */
    private String resolveVendorId(HttpServletRequest request) {
        String vendorId = authenticatedUserResolver.getCurrentUserId(request);
        if (vendorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Vendor identity could not be resolved. A valid JWT and reachable IAM service are required.");
        }
        return vendorId;
    }

    /**
     * Throws 403 if the given contractId does not belong to the authenticated vendor.
     * Used to guard delivery access — delivery ownership is checked via its contractId.
     */
    private void assertDeliveryOwnership(HttpServletRequest request, String contractId) {
        String vendorId = resolveVendorId(request);
        List<String> vendorContractIds = contractService.getContractIdsByVendorId(vendorId);
        if (!vendorContractIds.contains(contractId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: delivery belongs to a contract that is not associated with your vendor account.");
        }
    }
}
