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
 * 使用了开源项目的限流 Bucket4j
 *
 * @Author zhangdj
 * @Date 2021/5/31:18:26
 * @Description
 */
@Component
@Order(1)
public class BGlobalFilter implements GlobalFilter {
    //桶的最大容量，即能装载 Token 的最大数量
    int capacity = 100;
    //每次 Token 补充量
    int refillTokens = 5;
    //补充 Token 的时间间隔
    Duration duration = Duration.ofSeconds(1);

    private static final Map<String, Bucket> BUCKET_CACHE = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        // 令牌填充有两种 填充策略，间隔策略（intervally） 和 贪婪策略（greedy）
        // 贪婪策略【尽可能贪婪的填充令牌，会将一分钟划分成 5 个更小的时间单元，每隔 12 秒，填充 1 个令牌】。每分钟填充 5 个令牌
        Refill refill = Refill.greedy(refillTokens, duration);
        // 间隔策略【每隔一段时间，一次性的填充所有令牌】。每分钟填充 5 个令牌
//        Refill refill1 = Refill.intervally(refillTokens, duration);
        // 限流的规则，两种创建方式：.classic()，.simple()
        // 表示桶大小为 capacity:100，填充速度为每分钟 5 个令牌。classic 方式更灵活一点，可以自定义填充速度
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        // 表示桶大小为 10，填充速度为每分钟 10 个令牌。simple 方式桶大小和填充速度是一样的
//        Bandwidth limit = Bandwidth.simple(10, Duration.ofMinutes(1));
        // 创建令牌桶
        return Bucket4j.builder().addLimit(limit).build();
    }

    private final static Logger log = LoggerFactory.getLogger(BGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("BGlobalFilter前置逻辑");
        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        // 若key对应的value为空，会将第二个参数的返回值存入并返回。将令牌桶存入map中
        Bucket bucket = BUCKET_CACHE.computeIfAbsent(ip, V -> createNewBucket());

        System.out.println("IP: " + ip + "，has Tokens: " + bucket.getAvailableTokens());
        // bucket.tryConsume(1)消耗一个令牌
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                log.info("BGlobalFilter后置逻辑");
            }));
        } else {
            // 太多请求
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }
}