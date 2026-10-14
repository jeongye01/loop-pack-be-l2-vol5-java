package com.loopers.config.cache;

import com.loopers.support.cache.CacheVersions;
import com.loopers.support.cache.CacheKeys;
import com.loopers.like.domain.Like;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "catalog.cache.enabled", havingValue = "true")
public class CacheWriteConfiguration {
    @Bean
    static BeanPostProcessor cacheRevisionWrites(ObjectProvider<CacheVersions> versions) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                String type = AopUtils.getTargetClass(bean).getSimpleName();
                if (!java.util.Set.of("ProductRepositoryAdapter", "BrandRepositoryAdapter", "StockRepositoryAdapter", "LikeRepositoryAdapter").contains(type)) return bean;
                ProxyFactory proxy = new ProxyFactory(bean);
                proxy.setProxyTargetClass(true);
                proxy.addAdvice((MethodInterceptor) call -> {
                    Object result = call.proceed();
                    if (call.getMethod().getName().equals("save") || call.getMethod().getName().equals("delete")) {
                        String namespace = switch (type) {
                            case "StockRepositoryAdapter" -> CacheKeys.STOCK_REVISION;
                            case "LikeRepositoryAdapter" -> CacheKeys.userRevision(((Like) call.getArguments()[0]).getUserId());
                            default -> CacheKeys.CATALOG_REVISION;
                        };
                        versions.getObject().changed(namespace);
                    }
                    return result;
                });
                return proxy.getProxy();
            }
        };
    }
}
