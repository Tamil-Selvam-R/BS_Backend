package com.buildsmart.vendor.validator;

import com.buildsmart.vendor.exception.CustomExceptions.InvalidIdFormatException;

import java.util.regex.Pattern;

/**
 * Central utility for validating that every ID input matches its expected
 * format. Each entity has its own regex; a vendorId in a contractId field is
 * just as invalid as garbled text. This stops cross-entity ID confusion before
 * it can reach the persistence or integration layers.
 *
 * Format reference (kept in sync with IdGeneratorUtil and PM's IdGeneratorService):
 *   Vendor      BSVM\d{3}            e.g. BSVM001
 *   Contract    CONBS\d{3}           e.g. CONBS001
 *   Delivery    DELBS\d{3}           e.g. DELBS001
 *   Invoice     INVBS\d{3}           e.g. INVBS001
 *   Document    DOCBS\d{3}           e.g. DOCBS001
 *   Approval    APRVN\d{3}           e.g. APRVN001  (vendor-side flow only)
 *   Project     CHEBS\d{5}           e.g. CHEBS26001 (CHEBS + YY + 3-digit seq)
 *   Task        (FIN|VN|SE|SO|PM|ADM)\d{3}   e.g. VN001, SE042
 *
 * All checks are case-sensitive; PM and vendor generators emit upper-case only,
 * so accepting "bsvm001" would mask copy-paste bugs.
 */
public final class IdFormatValidator {

    public enum Kind {
        VENDOR("vendorId",    Pattern.compile("^BSVM\\d{3}$"),                    "BSVM<3 digits> e.g. BSVM001"),
        CONTRACT("contractId",Pattern.compile("^CONBS\\d{3}$"),                   "CONBS<3 digits> e.g. CONBS001"),
        DELIVERY("deliveryId",Pattern.compile("^DELBS\\d{3}$"),                   "DELBS<3 digits> e.g. DELBS001"),
        INVOICE("invoiceId",  Pattern.compile("^INVBS\\d{3}$"),                   "INVBS<3 digits> e.g. INVBS001"),
        DOCUMENT("documentId",Pattern.compile("^DOCBS\\d{3}$"),                   "DOCBS<3 digits> e.g. DOCBS001"),
        APPROVAL("approvalId",Pattern.compile("^APRVN\\d{3}$"),                   "APRVN<3 digits> e.g. APRVN001"),
        PROJECT("projectId",  Pattern.compile("^CHEBS\\d{5}$"),                   "CHEBS<2-digit year><3-digit seq> e.g. CHEBS26001"),
        TASK("taskId",        Pattern.compile("^(FIN|VN|SE|SO|PM|ADM)\\d{3}$"),   "<dept-prefix><3 digits> e.g. VN001, SE042");

        private final String defaultFieldName;
        private final Pattern pattern;
        private final String humanReadable;

        Kind(String defaultFieldName, Pattern pattern, String humanReadable) {
            this.defaultFieldName = defaultFieldName;
            this.pattern = pattern;
            this.humanReadable = humanReadable;
        }
    }

    private IdFormatValidator() {}

    /**
     * Validate {@code value} against the format for {@code kind}. Throws if
     * non-null and ill-formed. Use this for fields that are REQUIRED (the
     * "required" check belongs to entity validators; here we only complain
     * when a value is present-but-malformed). Pass null to skip cleanly.
     */
    public static void requireValid(Kind kind, String value) {
        requireValid(kind, value, kind.defaultFieldName);
    }

    /**
     * Same as {@link #requireValid(Kind, String)} with a custom field name in
     * the error message — useful when a controller has multiple IDs of the
     * same kind, or when the field is named differently in the request.
     */
    public static void requireValid(Kind kind, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!kind.pattern.matcher(value).matches()) {
            throw new InvalidIdFormatException(fieldName, value, kind.humanReadable);
        }
    }
}
