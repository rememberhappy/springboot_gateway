package com.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.gateway.utils.RemoteIPHost;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    @Resource
    RestTemplate restTemplate;

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
        System.out.println("ip：" + ip);// ip：127.0.0.1
        String test = restTemplateRequest("117.136.42.86");
        System.out.println("该ip所在的城市：" + test);// 所在省：北京市,所在市：北京市
        // 获取请求参数
        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        String momn = queryParams.getFirst("momn");
        System.out.println("请求参数-momn：" + momn);// 请求参数-momn：null
        String uname = queryParams.getFirst("uname");
        System.out.println("请求参数-uname：" + uname);// 请求参数-uname：null
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
        System.out.println("请求信息-sourceHost1【真实的】：" + sourceHost1);// 请求信息-sourceHost1【真实的】：0:0:0:0:0:0:0:1

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

    /**
     * 调用第三方接口  高德地图，根据IP识别IP所在的城市
     * 使用ResrTemplate调用第三方接口
     *
     * @param ip
     * @return java.lang.String
     * @Throws
     * @Author zhangdj
     * @date 2021/6/8 14:39
     */
    private String restTemplateRequest(String ip) {
        String url = "https://restapi.amap.com/v3/ip?key=65c7419e87599e8467876a3a5a93b86d&ip=" + ip;
//        HttpHeaders headers = new HttpHeaders();
//        //定义请求参数类型，这里用json所以是MediaType.APPLICATION_JSON
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        //RestTemplate带参传的时候要用HttpEntity<?>对象传递
//        Map<String, Object> map = new HashMap<String, Object>();
//        HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(map, headers);

        ResponseEntity<String> entity = restTemplate.getForEntity(url, String.class);
        //获取3方接口返回的数据通过entity.getBody();它返回的是一个字符串；
        String body = entity.getBody();
        //然后把str转换成JSON再通过getJSONObject()方法获取到里面的result对象，因为我想要的数据都在result里面
        //下面的strToJson只是一个str转JSON的一个共用方法；
        JSONObject json = JSONObject.parseObject(body);
        if (json != null) {
            Integer status = json.getInteger("status");// 值为0或1,0表示失败；1表示成功
            if (status == null || status.intValue() == 0) {
                System.out.println("第三方接口调用失败");
            }
            String province = json.getString("province");// 省份名称 若为直辖市则显示直辖市名称,如果在局域网 IP网段内，则返回“局域网”,非法IP以及国外IP则返回空
            String city = json.getString("city");// 城市名称 若为直辖市则显示直辖市名称，如果为局域网网段内IP或者非法IP或国外IP，则返回空
            if (StringUtils.isNotEmpty(province) && StringUtils.isNotEmpty(city)) {
                //调用JSONObject.toJavaObject()把JSON转成java对象最后抛出数据即可
                System.out.println("所在省：" + province + ",所在市：" + city);
                return "";
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * 调用第三方接口  高德地图，根据IP识别IP所在的城市
     * 使用WebClient调用第三方接口
     *
     * @param
     * @return java.lang.String
     * @Throws
     * @Author zhangdj
     * @date 2021/6/8 14:54
     */
    private String wenClientRequest(String ip) {
        String url = "https://restapi.amap.com/v3/ip?key=65c7419e87599e8467876a3a5a93b86d&ip=" + ip;
        Flux<String> stringFlux = WebClient.create()
                .get()
                .uri(url)
                // 获取响应体
                .retrieve()
                .bodyToFlux(String.class);
        //响应数据类型转换
        List<String> posts = stringFlux.collectList().block();
        return posts.get(0);
    }
}