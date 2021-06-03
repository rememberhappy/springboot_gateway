package com.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.gateway.utils.RemoteIPHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 * 定义A全局过滤器
 * 过滤器执行流程：order 越大，优先级越低。-1第一个执行，接下来是0，一直下排。最大值Ordered.LOWEST_PRECEDENCE
 *
 * @Author zhangdj
 * @Date 2021/5/31:17:54
 * @Description
 */
@Component
//@Order(Ordered.LOWEST_PRECEDENCE)// 最大值
@Order(-1)
public class AGlobalFilter implements GlobalFilter {
    private final static Logger log = LoggerFactory.getLogger(AGlobalFilter.class);
    private static final String ELAPSED_TIME_BEGIN = "elapsedTimeBegin";
    @Resource
    RedisTemplate<String, Integer> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 用来测试熔断机制。熔断策略中配置了过期时间为一秒，此处睡眠3秒。所有请求都会到熔断中
//        try {
//            Thread.sleep(3000l);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        log.info("AGlobalFilter前置逻辑");
        // 在请求刚刚到达时，往ServerWebExchange中放入了一个属性elapsedTimeBegin，属性值为当时的毫秒级时间戳
        exchange.getAttributes().put(ELAPSED_TIME_BEGIN, System.currentTimeMillis());
        Long start = System.currentTimeMillis();
        // 获取URL地址【这个url是经过配置文件StripPrefix切割过后的】
        String url = exchange.getRequest().getURI().getPath();
        System.out.println("url：" + url);
        // 获取IP地址
        String ip = RemoteIPHost.getRemoteHost(exchange.getRequest());
        System.out.println("ip：" + ip);
        // 获取请求参数
        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        String momn = queryParams.getFirst("momn");
        System.out.println("请求参数-momn：" + momn);
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        InetSocketAddress localAddress = exchange.getRequest().getLocalAddress();
        InetAddress address = remoteAddress.getAddress();
        System.out.println("请求信息-address【反向代理后的】：" + address);
        InetAddress address1 = localAddress.getAddress();
        System.out.println("请求信息-address1【真实的】：" + address1);
        int port = remoteAddress.getPort();
        System.out.println("请求信息-port【反向代理后的】：" + port);
        int port1 = localAddress.getPort();
        System.out.println("请求信息-port1【真实的】：" + port1);
        String hostString = remoteAddress.getHostString();
        System.out.println("请求信息-hostString【反向代理后的】：" + hostString);
        String hostString1 = localAddress.getHostString();
        System.out.println("请求信息-hostString1【真实的】：" + hostString1);
        String sourceHost = remoteAddress.getHostName();
        System.out.println("请求信息-sourceHost【反向代理后的】：" + sourceHost);
        String sourceHost1 = localAddress.getHostName();
        System.out.println("请求信息-sourceHost1【真实的】：" + sourceHost1);

        // 根据请求头中的数据，获取请求信息
        HttpHeaders headers = exchange.getRequest().getHeaders();
        // 获取请求头里面的信息
        String token = headers.getFirst("token");
        System.out.println("请求头信息-token：" + token);
        String organ = headers.getFirst("organ");
        System.out.println("请求头信息-organ：" + organ);
        // TODO: 2021/6/1 可以处理白名单，处理token，处理权限，处理非法请求，进行统计，限流，指定IP访问
        // 处理token
        if (token == null) {
            // token不符合规则，返回报错信息
            tokenNeedLogin(exchange);
//            tokenNeedLoginCode(exchange);
        }
        // 统计功能，统计每个url访问了多少次`
        Long end = System.currentTimeMillis();
        try {
            String timeSKey = "T-" + url;
            // 可以使用自增序列
            redisTemplate.opsForList().rightPush(timeSKey, (int) (end - start));
            Long size = redisTemplate.opsForList().size(timeSKey);
            if (size != null && size > 1000L) {
                redisTemplate.opsForList().trim(timeSKey, 100, size);
            }
        } catch (Exception e) {
            log.error("统计功能失败");
        }

        // 给请求添加请求头信息
        ServerHttpRequest.Builder mutate = exchange.getRequest().mutate();
        mutate.header("token", "token");
        mutate.header("userId", "123456");
        ServerHttpRequest serverHttpRequest = mutate.build();
        ServerWebExchange webExchange = exchange.mutate().request(serverHttpRequest).build();
        Mono<Void> res = chain.filter(webExchange);
        // 处理完成
        return res.then(Mono.fromRunnable(() -> {
            // 在请求执行结束后，又从中取出我们之前放进去的那个时间戳，与当前时间的差值即为该请求的耗时
            Long startTime = exchange.getAttribute(ELAPSED_TIME_BEGIN);
            if (startTime != null) {
                log.info(exchange.getRequest().getURI().getRawPath() + ": " + (System.currentTimeMillis() - startTime) + "ms");
            }
            log.info("AGlobalFilter后置逻辑");
        }));
    }

    /**
     * 在过滤中处理异常情况
     *
     * @param exchange
     * @return reactor.core.publisher.Mono<java.lang.Void>
     * @Throws
     * @Author zhangdj
     * @date 2021/6/1 10:58
     */
    public Mono<Void> tokenNeedLogin(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        HashMap<String, Object> map = new HashMap<>();
        byte[] datas = JSON.toJSONBytes(map.put("303", "请先登录"));
        DataBuffer buffer = response.bufferFactory().wrap(datas);
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }

    public Mono<Void> tokenNeedLoginCode(ServerWebExchange exchange) {
        log.info("*****头部验证不通过，请在头部输入  user-id");
        //终止请求，直接回应
        exchange.getResponse().setStatusCode(HttpStatus.NOT_ACCEPTABLE);
        return exchange.getResponse().setComplete();
    }
}