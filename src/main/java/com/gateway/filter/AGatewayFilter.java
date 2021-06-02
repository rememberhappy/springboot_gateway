package com.gateway.filter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 定义局部过滤器，需要经局部过滤器注入到过滤器工厂中【AGatewayFilterFactory】
 *
 * @Author zhangdj
 * @Date 2021/6/1:11:49
 * @Description
 */
@Component
@Order(2)
public class AGatewayFilter implements GatewayFilter {
    private static Logger log = LoggerFactory.getLogger(AGlobalFilter.class);

    /**
     * 局部过滤器举例, 对请求头部的 user-id 进行校验
     *
     * @param exchange
     * @param chain
     * @return reactor.core.publisher.Mono<java.lang.Void>
     * @Throws
     * @Author zhangdj
     * @date 2021/6/1 11:53
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("AGatewayFilter前置逻辑");
        String url = exchange.getRequest().getPath().pathWithinApplication().value();
        log.info("请求URL:" + url);
        log.info("method:" + exchange.getRequest().getMethod());

        // 获取param 请求参数
        String uname = exchange.getRequest().getQueryParams().getFirst("uname");
        if (StringUtils.isBlank(uname)) {
            log.info("*****    头部验证不通过，请在头部输入  user-id");
            //终止请求，直接回应。返回：406 Not Acceptable
            exchange.getResponse().setStatusCode(HttpStatus.NOT_ACCEPTABLE);
            return exchange.getResponse().setComplete();
        }
        // 获取请求头header
        String userId = exchange.getRequest().getHeaders().getFirst("user-id");
        log.info("userId：" + userId);
//        if (StringUtils.isBlank(userId)) {
//            log.info("*****    头部验证不通过，请在头部输入  user-id");
//            //终止请求，直接回应。返回：406 Not Acceptable
//            exchange.getResponse().setStatusCode(HttpStatus.NOT_ACCEPTABLE);
//            return exchange.getResponse().setComplete();
//        }
        return chain.filter(exchange);
    }
}