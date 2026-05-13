package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.ExpenseStatus;
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
public class ExpenseApprovalRequest {

    @NotNull(message = "Status is required")
    private ExpenseStatus status;

    @NotBlank(message = "Approved by is required")
    private String approvedBy;

    private String rejectionReason;

    private String revisionReason;
}
