package com.loopers.product.interfaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ListResponse;
import com.loopers.interfaces.api.SliceResponse;
import com.loopers.like.application.LikeUseCase;
import com.loopers.product.application.ProductUseCase;
import com.loopers.product.domain.ProductSort;
import com.loopers.support.cache.CacheKeys;
import com.loopers.support.cache.CacheVersions;
import com.loopers.support.cache.ResponseCache;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 상품 정보와 표시 값을 별도로 캐시하고 한 응답으로 합친다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReadFacade {
    private final ProductUseCase products;
    private final LikeUseCase likes;
    private final ResponseCache cache;

    public ApiResponse<SliceResponse<ProductV1Dto.CustomerProductResponse>> page(Long brandId, ProductSort sort, String normalized, int page, int size) {
        var revisions = cache.snapshot(CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION);
        ApiResponse<SliceResponse<ProductV1Dto.BasicProductResponse>> basic = cache.read(revisions,
            "likes_desc".equals(normalized) ? CacheKeys.RANKINGS : CacheKeys.PRODUCTS,
            CacheKeys.products(normalized, brandId, page, size), new TypeReference<>() { },
            () -> ApiResponse.success(SliceResponse.from(products.findBasicPage(brandId, sort, page, size), ProductV1Dto.BasicProductResponse::from)),
            CacheKeys.CATALOG_REVISION);
        var data = basic.data();
        return ApiResponse.success(new SliceResponse<>(compose(data.content(), revisions), data.page(), data.size(), data.last()));
    }

    public ApiResponse<ProductV1Dto.CustomerProductResponse> detail(Long id) {
        var revisions = cache.snapshot(CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION);
        ApiResponse<ProductV1Dto.BasicProductResponse> basic = cache.read(revisions, CacheKeys.PRODUCTS, CacheKeys.product(id), new TypeReference<>() { },
            () -> ApiResponse.success(ProductV1Dto.BasicProductResponse.from(products.findBasicProduct(id))), CacheKeys.CATALOG_REVISION);
        return ApiResponse.success(compose(List.of(basic.data()), revisions).get(0));
    }

    public ApiResponse<ListResponse<ProductV1Dto.CustomerProductResponse>> mine(Long userId, int page, int size) {
        var revisions = cache.snapshot(CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION, CacheKeys.userRevision(userId));
        ApiResponse<ListResponse<ProductV1Dto.BasicProductResponse>> basic = cache.read(revisions, CacheKeys.MY_LIKES,
            CacheKeys.myLikes(userId, page, size), new TypeReference<>() { },
            () -> ApiResponse.success(ListResponse.from(likes.findMineBasicPage(userId, userId, page, size), ProductV1Dto.BasicProductResponse::from)),
            CacheKeys.CATALOG_REVISION, CacheKeys.userRevision(userId));
        var data = basic.data();
        return ApiResponse.success(new ListResponse<>(compose(data.content(), revisions), data.page(), data.size(), data.totalElements()));
    }

    public ApiResponse<List<ProductUseCase.LikeCount>> counts(List<Long> ids) {
        return counts(ids, cache.snapshot(CacheKeys.CATALOG_REVISION));
    }
    private ApiResponse<List<ProductUseCase.LikeCount>> counts(List<Long> ids, CacheVersions.Snapshot revisions) {
        List<Long> normalized = ids.stream().distinct().sorted().toList();
        return cache.read(revisions, CacheKeys.COUNTS, CacheKeys.batch(CacheKeys.COUNTS, normalized), new TypeReference<>() { },
            () -> ApiResponse.success(products.findLikeCounts(normalized)), CacheKeys.CATALOG_REVISION);
    }

    public ApiResponse<List<ProductUseCase.StockStatus>> stocks(List<Long> ids) {
        return stocks(ids, cache.snapshot(CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION));
    }
    private ApiResponse<List<ProductUseCase.StockStatus>> stocks(List<Long> ids, CacheVersions.Snapshot revisions) {
        List<Long> normalized = ids.stream().distinct().sorted().toList();
        return cache.read(revisions, CacheKeys.STOCKS, CacheKeys.batch(CacheKeys.STOCKS, normalized), new TypeReference<>() { },
            () -> ApiResponse.success(products.findStockStatuses(normalized)), CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION);
    }

    private List<ProductV1Dto.CustomerProductResponse> compose(List<ProductV1Dto.BasicProductResponse> basic, CacheVersions.Snapshot revisions) {
        if (basic.isEmpty()) return List.of();
        var ids = basic.stream().map(ProductV1Dto.BasicProductResponse::id).distinct().sorted().toList();
        var values = cache.readPair(revisions,
            new ResponseCache.Entry<>(CacheKeys.COUNTS, CacheKeys.batch(CacheKeys.COUNTS, ids),
                new TypeReference<ApiResponse<List<ProductUseCase.LikeCount>>>() { },
                () -> ApiResponse.success(products.findLikeCounts(ids)), CacheKeys.CATALOG_REVISION),
            new ResponseCache.Entry<>(CacheKeys.STOCKS, CacheKeys.batch(CacheKeys.STOCKS, ids),
                new TypeReference<ApiResponse<List<ProductUseCase.StockStatus>>>() { },
                () -> ApiResponse.success(products.findStockStatuses(ids)), CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION));
        var counts = values.first().data().stream().collect(Collectors.toMap(ProductUseCase.LikeCount::productId, Function.identity()));
        var stocks = values.second().data().stream().collect(Collectors.toMap(ProductUseCase.StockStatus::productId, Function.identity()));
        return basic.stream().map(item -> new ProductV1Dto.CustomerProductResponse(item.id(), item.name(), item.price(), item.brand(),
            counts.get(item.id()).likeCount(), stocks.get(item.id()).soldOut())).toList();
    }
}
