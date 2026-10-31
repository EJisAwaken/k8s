package org.esoalahy.stock.core.pagination;

import org.springframework.data.domain.Page;

import java.util.List;

/** Page de résultats sérialisable de façon stable (le JSON de {@code Page} n'est pas garanti). */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageDto<T> of(Page<T> p) {
        return new PageDto<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
