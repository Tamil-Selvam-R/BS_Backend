package com.buildsmart.finance.dto.request;

import com.buildsmart.finance.entity.enums.BudgetStatus;
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
public class BudgetApprovalRequest {

    @NotNull(message = "Status is required")
    private BudgetStatus status;

    @NotBlank(message = "Approved by is required")
    private String approvedBy;

    private String rejectionReason;
}
