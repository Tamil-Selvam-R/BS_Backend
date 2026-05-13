package com.buildsmart.finance.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "createdAt";
    private static final Sort.Direction DEFAULT_SORT_ORDER = Sort.Direction.DESC;

    /**
     * Create Pageable from pagination parameters
     */
    public static Pageable createPageable(Integer page, Integer size, String sortBy, String sortOrder) {
        int pageNum = page != null && page >= 0 ? page : DEFAULT_PAGE;
        int pageSize = size != null && size > 0 && size <= MAX_SIZE ? size : DEFAULT_SIZE;
        String sortByField = sortBy != null && !sortBy.isBlank() ? sortBy : DEFAULT_SORT_BY;
        
        Sort.Direction direction = DEFAULT_SORT_ORDER;
        if (sortOrder != null && !sortOrder.isBlank()) {
            try {
                direction = Sort.Direction.fromString(sortOrder.toUpperCase());
            } catch (IllegalArgumentException e) {
                direction = DEFAULT_SORT_ORDER;
            }
        }

        return PageRequest.of(pageNum, pageSize, Sort.by(direction, sortByField));
    }

    /**
     * Validate page size
     */
    public static int validatePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * Validate page number
     */
    public static int validatePageNumber(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }
}
