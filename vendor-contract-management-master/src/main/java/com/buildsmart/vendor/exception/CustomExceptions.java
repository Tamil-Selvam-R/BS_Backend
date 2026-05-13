package com.buildsmart.vendor.exception;

import java.util.Map;

public class CustomExceptions {

    private CustomExceptions() {
    }

    // ── Base ────────────────────────────────────────────────
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    // ── Not-Found per entity ────────────────────────────────
    public static class ContractNotFoundException extends ResourceNotFoundException {
        public ContractNotFoundException(String contractId) {
            super("Contract not found with ID: " + contractId);
        }
    }

    public static class InvoiceNotFoundException extends ResourceNotFoundException {
        public InvoiceNotFoundException(String invoiceId) {
            super("Invoice not found with ID: " + invoiceId);
        }
    }

    public static class DeliveryNotFoundException extends ResourceNotFoundException {
        public DeliveryNotFoundException(String deliveryId) {
            super("Delivery not found with ID: " + deliveryId);
        }
    }

    public static class DocumentNotFoundException extends ResourceNotFoundException {
        public DocumentNotFoundException(String documentId) {
            super("Document not found with ID: " + documentId);
        }
    }

    public static class ProjectNotFoundException extends ResourceNotFoundException {
        public ProjectNotFoundException(String projectId) {
            super("Project not found with ID: " + projectId
                    + ". The project does not exist in the Project Manager module.");
        }
    }

    /**
     * Thrown when a vendor references a projectId that either doesn't exist
     * in PM, or exists in PM but has no tasks assigned to this vendor. From
     * the vendor's perspective these are indistinguishable on purpose — both
     * surface as "project does not exist" so we don't leak the existence of
     * other vendors' projects.
     */
    public static class ProjectNotAccessibleException extends ResourceNotFoundException {
        public ProjectNotAccessibleException(String projectId) {
            super("Project does not exist: " + projectId
                    + ". Either the project is unknown to the system or it has no tasks assigned to you.");
        }
    }

    /**
     * Thrown when a vendor references a taskId that either doesn't exist in
     * the given project, or exists but is assigned to someone else.
     */
    public static class TaskNotFoundException extends ResourceNotFoundException {
        public TaskNotFoundException(String taskId) {
            super("Task not found: " + taskId
                    + ". Either the task does not exist or it is not assigned to you.");
        }
    }

    /**
     * Thrown when an invoice would push the total billed amount for a contract
     * past the contract's value. Carries structured context so the API
     * response can show the breakdown to the vendor.
     */
    public static class InvoiceExceedsContractValueException extends RuntimeException {
        private final String contractId;
        private final java.math.BigDecimal contractValue;
        private final java.math.BigDecimal alreadyBilled;
        private final java.math.BigDecimal thisInvoiceAmount;
        private final java.math.BigDecimal overage;

        public InvoiceExceedsContractValueException(String contractId,
                                                    java.math.BigDecimal contractValue,
                                                    java.math.BigDecimal alreadyBilled,
                                                    java.math.BigDecimal thisInvoiceAmount) {
            super(String.format(
                    "Invoice amount %s would exceed contract %s value %s. "
                            + "Already billed (excluding rejected): %s. Maximum allowed for this invoice: %s.",
                    thisInvoiceAmount, contractId, contractValue, alreadyBilled,
                    contractValue.subtract(alreadyBilled)));
            this.contractId = contractId;
            this.contractValue = contractValue;
            this.alreadyBilled = alreadyBilled;
            this.thisInvoiceAmount = thisInvoiceAmount;
            this.overage = thisInvoiceAmount.subtract(contractValue.subtract(alreadyBilled));
        }

        public String getContractId() { return contractId; }
        public java.math.BigDecimal getContractValue() { return contractValue; }
        public java.math.BigDecimal getAlreadyBilled() { return alreadyBilled; }
        public java.math.BigDecimal getThisInvoiceAmount() { return thisInvoiceAmount; }
        public java.math.BigDecimal getOverage() { return overage; }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    // ── Validation / Business-rule exceptions ───────────────
    public static class ValidationException extends RuntimeException {
        private final Map<String, String> errors;

        public ValidationException(Map<String, String> errors) {
            super("Validation failed");
            this.errors = errors;
        }

        public Map<String, String> getErrors() {
            return errors;
        }
    }

    public static class InvalidDateRangeException extends RuntimeException {
        public InvalidDateRangeException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }

    public static class DuplicateResourceException extends RuntimeException {
        public DuplicateResourceException(String message) {
            super(message);
        }
    }

    public static class InvalidIdFormatException extends RuntimeException {
        private final String fieldName;
        private final String value;
        private final String expectedFormat;

        public InvalidIdFormatException(String fieldName, String value, String expectedFormat) {
            super(fieldName + " '" + value + "' is not valid. Expected format: " + expectedFormat);
            this.fieldName = fieldName;
            this.value = value;
            this.expectedFormat = expectedFormat;
        }

        public String getFieldName() { return fieldName; }
        public String getValue() { return value; }
        public String getExpectedFormat() { return expectedFormat; }
    }
}

