package com.buildsmart.siteops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper for all list endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    
    @JsonProperty("content")
    private List<T> content;
    
    @JsonProperty("pageNumber")
    private int pageNumber;
    
    @JsonProperty("pageSize")
    private int pageSize;
    
    @JsonProperty("totalElements")
    private long totalElements;
    
    @JsonProperty("totalPages")
    private int totalPages;
    
    @JsonProperty("isLastPage")
    private boolean isLastPage;
    
    @JsonProperty("sortBy")
    private String sortBy;
    
    @JsonProperty("sortDirection")
    private String sortDirection;
}

