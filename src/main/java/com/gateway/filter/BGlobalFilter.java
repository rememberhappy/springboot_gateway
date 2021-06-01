package com.gateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流的示例
 *
 * @Author zhangdj
 * @Date 2021/5/31:18:26
 * @Description
 */
@Component
@Order(1)
public class BGlobalFilter implements GlobalFilter {
    int capacity = 5;//桶的最大容量，即能装载 Token 的最大数量

    int refillTokens = 1; //每次 Token 补充量

    Duration duration = Duration.ofSeconds(1); //补充 Token 的时间间隔

    private static final Map<String, Bucket> BUCKET_CACHE = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        Refill refill = Refill.greedy(refillTokens, duration);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket4j.builder().addLimit(limit).build();
    }

    private static Logger log = LoggerFactory.getLogger(BGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("BGlobalFilter前置逻辑");
        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        // 若key对应的value为空，会将第二个参数的返回值存入并返回
        Bucket bucket = BUCKET_CACHE.computeIfAbsent(ip, V -> createNewBucket());

        System.out.println("IP: " + ip + "，has Tokens: " + bucket.getAvailableTokens());
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        } else {
            // 太多请求
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
//        return chain.filter(exchange).then(Mono.fromRunnable(() ->
//        {
//            log.info("BGlobalFilter后置逻辑");
//        }));
    }
}