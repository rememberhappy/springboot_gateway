package com.gateway.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 过滤器Hystrix中的服务降级
 *
 * @Author zhangdj
 * @Date 2021/5/31:17:07
 * @Description
 */
@RestController
public class FallbackController {

    @GetMapping("/fallbackA")
    public ResponseEntity<String> fallbackA() {
        // 返回错误信息
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Baeldung-Example-Header",
                "Value-ResponseEntityBuilderWithHttpHeaders");
        return ResponseEntity.status(400)
                .headers(responseHeaders)
                .body("响应信息");
    }
}