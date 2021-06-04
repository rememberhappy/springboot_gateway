package com.gateway.resolver;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 通过IP进行限流
 * 暂时无效
 *
 * @Author zhangdj
 * @Date 2021/6/2:16:58
 * @Description
 */
public class HostAddrKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
//        return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        return null;
    }
}