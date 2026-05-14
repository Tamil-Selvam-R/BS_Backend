package com.buildsmart.vendor.controller;

import com.buildsmart.vendor.dto.response.ContractResponse;
import com.buildsmart.vendor.dto.request.ContractRequest;
import com.buildsmart.vendor.enums.ContractStatus;
import com.buildsmart.vendor.exception.CustomExceptions.UnauthorizedException;
import com.buildsmart.vendor.security.AuthenticatedUserResolver;
import com.buildsmart.vendor.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Contract REST controller.
 *
 * Authorization policy on GET endpoints:
 *   - VENDOR: sees only their own contracts (vendorId from JWT).
 *   - ADMIN / PROJECT_MANAGER: unrestricted access to all contracts.
 */
@RestController
@RequestMapping("/api/contracts")
@Tag(name = "Contract", description = "Contract management APIs")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @Autowired
    private AuthenticatedUserResolver authenticatedUserResolver;

    // ── GET endpoints — vendor-scoped ────────────────────────────────────────

    @Operation(
            summary = "Get all contracts (paginated)",
            description = "**VENDOR**: returns only the calling vendor's own contracts.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all contracts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    public Page<ContractResponse> getAllContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "contractId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest httpRequest) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        if (isVendor()) {
            String vendorId = resolveAuthenticatedVendorId(httpRequest);
            return contractService.getContractsByVendorIdPaginated(vendorId, pageable);
        }
        return contractService.getAllContracts(pageable);
    }

    @Operation(
            summary = "Get contract by ID",
            description = "**VENDOR**: returns 403 if the contract does not belong to the calling vendor.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: unrestricted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract found"),
            @ApiResponse(responseCode = "400", description = "Invalid contract ID format"),
            @ApiResponse(responseCode = "403", description = "Vendor accessing another vendor's contract"),
            @ApiResponse(responseCode = "404", description = "Contract not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getContractById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.CONTRACT, id, "contractId");
        ContractResponse contract = contractService.getContractById(id);
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }
        if (isVendor()) {
            String vendorId = resolveAuthenticatedVendorId(httpRequest);
            assertContractOwnership(vendorId, contract);
        }
        return ResponseEntity.ok(contract);
    }

    @Operation(
            summary = "Get contracts by vendor ID",
            description = "**VENDOR**: only allowed to query their own vendorId — returns 403 for any other vendorId.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: can query any vendorId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts"),
            @ApiResponse(responseCode = "403", description = "Vendor querying another vendor's contracts")
    })
    @GetMapping("/vendor/{vendorId}")
    public List<ContractResponse> getContractsByVendorId(
            @Parameter(description = "Vendor ID to query", required = true)
            @PathVariable String vendorId,
            HttpServletRequest httpRequest) {

        com.buildsmart.vendor.validator.IdFormatValidator.requireValid(
                com.buildsmart.vendor.validator.IdFormatValidator.Kind.VENDOR, vendorId);
        if (isVendor()) {
            String currentVendorId = resolveAuthenticatedVendorId(httpRequest);
            if (!currentVendorId.equals(vendorId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: you can only view your own contracts.");
            }
        }
        return contractService.getContractsByVendorId(vendorId);
    }

    @Operation(
            summary = "Get contracts by status",
            description = "**VENDOR**: returns only their own contracts with the given status.\n\n" +
                    "**ADMIN / PROJECT_MANAGER**: returns all contracts with the given status.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts")
    @GetMapping("/status/{status}")
    public List<ContractResponse> getContractsByStatus(
            @PathVariable ContractStatus status,
            HttpServletRequest httpRequest) {

        if (isVendor()) {
            String vendorId = resolveAuthenticatedVendorId(httpRequest);
            return contractService.getContractsByVendorIdAndStatus(vendorId, status);
        }
        return contractService.getContractsByStatus(status);
    }

    // ── Mutation endpoints (VENDOR role only, unchanged) ────────────────────

    @Operation(summary = "Create a new contract",
            description = "Creates a new contract record. The vendorId is derived from the authenticated vendor's JWT.")
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
            description = "Updates an existing contract. The vendorId is derived from the JWT, preventing reassignment to another vendor.")
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True when the current JWT holder has the VENDOR role. */
    private boolean isVendor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
    }

    /**
     * Resolves the vendor's IAM userId from the JWT via AuthenticatedUserResolver.
     * Throws 401 if identity cannot be determined.
     */
    private String resolveAuthenticatedVendorId(HttpServletRequest httpRequest) {
        String vendorId = authenticatedUserResolver.getCurrentUserId(httpRequest);
        if (vendorId == null) {
            throw new UnauthorizedException(
                    "Vendor identity could not be resolved. A valid JWT and reachable IAM service are required.");
        }
        return vendorId;
    }

    /** Throws 403 if the loaded contract does not belong to the authenticated vendor. */
    private void assertContractOwnership(String currentVendorId, ContractResponse contract) {
        if (!currentVendorId.equals(contract.getVendorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: contract '" + contract.getContractId() + "' does not belong to your vendor account.");
        }
    }
}
