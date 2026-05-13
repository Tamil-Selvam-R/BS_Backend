package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentApprovalRequest {

    @NotNull(message = "Status is required")
    private PaymentStatus status;

    @NotBlank(message = "Approved by is required")
    private String approvedBy;

    private String rejectionReason;
}
