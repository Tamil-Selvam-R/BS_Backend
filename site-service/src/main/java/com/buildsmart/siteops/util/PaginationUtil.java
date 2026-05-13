package com.buildsmart.siteops.util;

import com.buildsmart.siteops.dto.PaginatedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Utility class for handling pagination and sorting operations.
 */
public class PaginationUtil {

    private static final int MIN_PAGE_NUMBER = 0;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String DEFAULT_SORT_DIRECTION = "DESC";

    /**
     * Creates a Pageable object with validation.
     *
     * @param pageNumber Page number (0-indexed)
     * @param pageSize   Number of items per page
     * @param sortBy     Field to sort by
     * @param sortDirection Sort direction (ASC/DESC)
     * @return Pageable object for Spring Data JPA
     */
    public static Pageable getPageable(int pageNumber, int pageSize, String sortBy, String sortDirection) {
        // Validate page number
        if (pageNumber < MIN_PAGE_NUMBER) {
            pageNumber = MIN_PAGE_NUMBER;
        }

        // Validate and constrain page size
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        // Determine sort order
        Sort.Direction direction = isSortDirectionValid(sortDirection) 
                ? Sort.Direction.fromString(sortDirection.toUpperCase()) 
                : Sort.Direction.fromString(DEFAULT_SORT_DIRECTION);

        // Create sort and pageable
        Sort sort = Sort.by(direction, sortBy);
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    /**
     * Creates a Pageable object with default sorting.
     *
     * @param pageNumber Page number (0-indexed)
     * @param pageSize   Number of items per page
     * @param sortBy     Field to sort by
     * @return Pageable object
     */
    public static Pageable getPageable(int pageNumber, int pageSize, String sortBy) {
        return getPageable(pageNumber, pageSize, sortBy, DEFAULT_SORT_DIRECTION);
    }

    /**
     * Creates a Pageable object with full defaults.
     *
     * @param pageNumber Page number
     * @param pageSize   Page size
     * @return Pageable object
     */
    public static Pageable getPageable(int pageNumber, int pageSize) {
        return getPageable(pageNumber, pageSize, "createdAt", DEFAULT_SORT_DIRECTION);
    }

    /**
     * Creates a Pageable with endpoint-level sort whitelist support.
     */
    public static Pageable getPageable(
            int pageNumber,
            int pageSize,
            String sortBy,
            String sortDirection,
            Set<String> allowedSortFields,
            String defaultSortBy) {

        String resolvedSortBy = normalizeSortBy(sortBy, allowedSortFields, defaultSortBy);
        return getPageable(pageNumber, pageSize, resolvedSortBy, sortDirection);
    }

    /**
     * Validates if the sort direction is valid (ASC or DESC).
     *
     * @param sortDirection Sort direction string
     * @return true if valid, false otherwise
     */
    private static boolean isSortDirectionValid(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return false;
        }
        return sortDirection.equalsIgnoreCase("ASC") || sortDirection.equalsIgnoreCase("DESC");
    }

    /**
     * Converts a Spring Data Page to a PaginatedResponse.
     *
     * @param page Spring Data Page object
     * @param sortBy Field that was sorted by
     * @param sortDirection Sort direction used
     * @param <T> Type of content
     * @return PaginatedResponse wrapper
     */
    public static <T> PaginatedResponse<T> toPaginatedResponse(
            Page<T> page, String sortBy, String sortDirection) {
        
        return PaginatedResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLastPage(page.isLast())
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();
    }

    /**
     * Normalizes sort direction to uppercase for consistency.
     *
     * @param sortDirection Sort direction (ASC/DESC, case-insensitive)
     * @return Normalized sort direction or default if invalid
     */
    public static String normalizeSortDirection(String sortDirection) {
        if (isSortDirectionValid(sortDirection)) {
            return sortDirection.toUpperCase();
        }
        return DEFAULT_SORT_DIRECTION;
    }

    /**
     * Normalizes sortBy to a safe value allowed by the endpoint.
     */
    public static String normalizeSortBy(String sortBy, Set<String> allowedSortFields, String defaultSortBy) {
        if (allowedSortFields == null || allowedSortFields.isEmpty()) {
            return (sortBy == null || sortBy.isBlank()) ? defaultSortBy : sortBy;
        }
        if (sortBy == null || sortBy.isBlank()) {
            return defaultSortBy;
        }
        return allowedSortFields.contains(sortBy) ? sortBy : defaultSortBy;
    }
}
