package com.gateway.FilterFactory;

import com.gateway.filter.AGatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * 过滤器工厂，将局部过滤器注入到局部过滤器工厂中
 *
 * @Author zhangdj
 * @Date 2021/6/1:12:02
 * @Description
 */
@Component
public class AGatewayFilterFactory extends AbstractGatewayFilterFactory {

    @Override
    public GatewayFilter apply(Object config) {
        return new AGatewayFilter();
    }
}