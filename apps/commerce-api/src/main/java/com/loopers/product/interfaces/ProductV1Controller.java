package com.loopers.product.interfaces;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ProductIds;
import com.loopers.support.cache.CacheKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import com.loopers.interfaces.api.SliceResponse;
import com.loopers.interfaces.api.PageQuery;
import com.loopers.interfaces.api.Requester;
import com.loopers.product.application.ProductUseCase;
import com.loopers.product.domain.ProductSort;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductUseCase productUseCase;
    private final com.loopers.support.cache.ResponseCache cache;

    @GetMapping
    public ApiResponse<SliceResponse<ProductV1Dto.CustomerProductResponse>> getProducts(
        Requester requester,
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false) String sort,
        @RequestParam(defaultValue = PageQuery.DEFAULT_PAGE) int page,
        @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE) int size
    ) {
        PageQuery query = new PageQuery(page, size);
        ProductSort requested = toSort(sort);
        String normalized = requested == null ? "latest" : sort;
        return cache.read("likes_desc".equals(normalized) ? CacheKeys.RANKINGS : CacheKeys.PRODUCTS,
            CacheKeys.products(normalized, brandId, page, size), new TypeReference<>() { },
            () -> ApiResponse.success(SliceResponse.from(
                productUseCase.findBasicPage(brandId, requested, query.page(), query.size()), ProductV1Dto.CustomerProductResponse::from)),
            CacheKeys.CATALOG_REVISION);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.CustomerProductResponse> getProduct(
        Requester requester,
        @PathVariable Long productId
    ) {
        return cache.read(CacheKeys.PRODUCTS, CacheKeys.product(productId), new TypeReference<>() { },
            () -> ApiResponse.success(ProductV1Dto.CustomerProductResponse.from(productUseCase.findBasicProduct(productId))),
            CacheKeys.CATALOG_REVISION);
    }

    @GetMapping("/like-counts")
    public ApiResponse<List<ProductUseCase.LikeCount>> getLikeCounts(Requester requester, @RequestParam String ids) {
        List<Long> normalized = ProductIds.parse(ids);
        return cache.read(CacheKeys.COUNTS, CacheKeys.batch(CacheKeys.COUNTS, normalized), new TypeReference<>() { },
            () -> ApiResponse.success(productUseCase.findLikeCounts(normalized)), CacheKeys.CATALOG_REVISION);
    }

    @GetMapping("/stocks")
    public ApiResponse<List<ProductUseCase.StockStatus>> getStocks(Requester requester, @RequestParam String ids) {
        List<Long> normalized = ProductIds.parse(ids);
        return cache.read(CacheKeys.STOCKS, CacheKeys.batch(CacheKeys.STOCKS, normalized), new TypeReference<>() { },
            () -> ApiResponse.success(productUseCase.findStockStatuses(normalized)), CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION);
    }

    /**
     * 정렬값이 없으면 null을 넘겨 application의 기본 정렬을 쓴다.
     */
    private static ProductSort toSort(String sort) {
        if (sort == null) {
            return null;
        }
        return switch (sort) {
            case "latest" -> ProductSort.LATEST;
            case "price_asc" -> ProductSort.PRICE_ASC;
            case "likes_desc" -> ProductSort.LIKES_DESC;
            default -> throw new CoreException(ErrorCode.INVALID_REQUEST);
        };
    }
}
