package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.ContractResponse;
import com.buildsmart.vendor.dto.request.ContractRequest;
import com.buildsmart.vendor.enums.ContractStatus;
import com.buildsmart.vendor.exception.CustomExceptions.UnauthorizedException;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.ContractService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@Tag(name = "Contract", description = "Contract management APIs")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @Autowired
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "Get all contracts", description = "Retrieves a paginated list of all contracts")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved all contracts")
    @GetMapping
    public Page<ContractResponse> getAllContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "contractId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return contractService.getAllContracts(pageable);
    }

    @Operation(summary = "Get contract by ID", description = "Retrieves a contract by its unique ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract found"),
            @ApiResponse(responseCode = "400", description = "Invalid contract ID format"),
            @ApiResponse(responseCode = "404", description = "Contract not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getContractById(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, id, "contractId");
        ContractResponse contract = contractService.getContractById(id);
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(contract);
    }

    @Operation(summary = "Get contracts by vendor ID", description = "Retrieves contracts for a specific vendor")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts")
    @GetMapping("/vendor/{vendorId}")
    public List<ContractResponse> getContractsByVendorId(@PathVariable String vendorId) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.VENDOR, vendorId);
        return contractService.getContractsByVendorId(vendorId);
    }

    @Operation(summary = "Get contracts by status", description = "Retrieves contracts filtered by status")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts")
    @GetMapping("/status/{status}")
    public List<ContractResponse> getContractsByStatus(@PathVariable ContractStatus status) {
        return contractService.getContractsByStatus(status);
    }

    @Operation(summary = "Create a new contract",
            description = "Creates a new contract record. The vendorId is derived "
                    + "from the authenticated vendor's JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT")
    })
    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ContractResponse createContract(@RequestBody ContractRequest request, HttpServletRequest httpRequest) {
        String vendorId = resolveAuthenticatedVendorId(httpRequest);
        return contractService.createContract(request, vendorId);
    }

    @Operation(summary = "Update a contract",
            description = "Updates an existing contract by ID. The vendorId is derived "
                    + "from the authenticated vendor's JWT, preventing reassignment of "
                    + "a contract to another vendor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Vendor identity could not be resolved from JWT"),
            @ApiResponse(responseCode = "404", description = "Contract not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ContractResponse> updateContract(
            @PathVariable String id,
            @RequestBody ContractRequest request,
            HttpServletRequest httpRequest) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, id, "contractId");
        String vendorId = resolveAuthenticatedVendorId(httpRequest);
        ContractResponse updated = contractService.updateContract(id, request, vendorId);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete a contract", description = "Deletes a contract by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Contract not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteContract(@PathVariable String id) {
        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, id, "contractId");
        contractService.deleteContract(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Resolve the vendor's IAM userId from the JWT. Throws 401 if the vendor's
     * identity cannot be determined — without it we cannot safely persist
     * a contract.
     */
    private String resolveAuthenticatedVendorId(HttpServletRequest httpRequest) {
        String authenticatedVendorId = authenticatedUserResolver.getCurrentUserId(httpRequest);
        if (authenticatedVendorId == null) {
            throw new UnauthorizedException(
                    "Vendor identity could not be resolved. A valid JWT and reachable IAM service are required.");
        }
        return authenticatedVendorId;
    }
}
