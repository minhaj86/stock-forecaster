package com.stockforecast.shared.api;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope: {@code {content: [...], page: {number, size, totalElements, totalPages}}}.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public static <S, T> PageResponse<T> of(List<S> content, int number, int size, long totalElements,
                                            Function<S, T> mapper) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content.stream().map(mapper).toList(),
                new PageMetadata(number, size, totalElements, totalPages));
    }
}
