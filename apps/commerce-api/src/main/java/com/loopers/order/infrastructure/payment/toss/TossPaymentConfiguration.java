package com.loopers.order.infrastructure.payment.toss;

import com.loopers.order.domain.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class TossPaymentConfiguration {

    @Bean
    public PaymentGateway paymentGateway(
        @Value("${payment.toss.base-url:https://api.tosspayments.com}") URI baseUrl,
        @Value("${payment.toss.secret-key:}") String secretKey,
        @Value("${payment.toss.connect-timeout:2s}") Duration connectTimeout,
        @Value("${payment.toss.read-timeout:3s}") Duration readTimeout
    ) {
        return new TossPaymentAdapter(TossHttpClientFactory.create(
            baseUrl, secretKey, connectTimeout, readTimeout));
    }
}
