package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Transactional
class LikeRepositoryIntegrationTest {

    @Autowired
    private LikeRepository repository;
    @Autowired
    private EntityManager entityManager;

    @DisplayName("[R-LIKE-05] 상품의 좋아요 수는 좋아요 관계에서 조회한다.")
    @Nested
    class CountRelations {
        @DisplayName("[동등 클래스 분할] 두 고객의 관계를 flush·clear한 뒤 집계하면 2다.")
        @Test
        void countsPersistedRelations() {
            repository.save(new Like(1L, 10L));
            repository.save(new Like(2L, 10L));
            flushAndClear();

            assertThat(repository.countByProductId(10L)).isEqualTo(2L);
        }
    }

    @DisplayName("[R-LIKE-07] 삭제된 상품은 내 좋아요 목록에서 제외한다.")
    @Nested
    class ExcludeDeletedProducts {
        @DisplayName("[상태 전이] 상품 삭제 후 flush·clear하면 관계는 남지만 내 목록에서는 빠진다.")
        @Test
        void excludesDeletedProductAndKeepsRelation() {
            Product product = new Product(1L, "상품", 100L);
            entityManager.persist(product);
            entityManager.flush();
            repository.save(new Like(1L, product.getId()));
            product.delete();
            flushAndClear();

            List<Like> result = repository.findAllByUserId(1L, 0, 20);

            assertThat(result).isEmpty();
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
