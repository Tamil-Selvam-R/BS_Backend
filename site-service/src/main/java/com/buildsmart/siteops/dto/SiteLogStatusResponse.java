package com.buildsmart.siteops.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteLogStatusResponse {
    private String logId;
    private String status;
    private String message;
}
