package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/16/16:29
 * @Description:
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {


        //拦截所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .order(0);//其实如果不设置order，默认都是0，就是先添加的先执行，
        //不过这里为了严谨，还是设置一下order的大小

        //拦截需要登录的请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(//放行的请求
                        "/user/code",
                        "/user/login",
                        "/bolg/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                )
                .order(1);


    }
}
