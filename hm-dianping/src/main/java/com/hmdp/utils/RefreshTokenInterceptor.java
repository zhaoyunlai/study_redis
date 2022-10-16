package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/16/16:23
 * @Description:
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {


    private final StringRedisTemplate stringRedisTemplate;

    //由于这个拦截器对象并没有交给Spring管理，所以不能够自动注入，所以使用有参的构造函数
    //在配置文件中用到了这个拦截的时候，将stringRedisTemplate对象传进来
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        //2、基于token获取redis中的用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //3、判断用户是否存在
        if(userMap.isEmpty()){
            //4、不存在直接放行
            return true;
        }

        //5、将查询到的Hash数据转成UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6、存在，保存用户信息到ThreadLocal
        //ThreadLocal是保存在当前线程中的
         UserHolder.saveUser(userDTO);

        //7、刷新token有效期
        stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8、放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
