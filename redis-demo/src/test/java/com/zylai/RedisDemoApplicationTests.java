package com.zylai;

import com.zylai.reids.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.jnlp.UnavailableServiceException;

@SpringBootTest
class RedisDemoApplicationTests {

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Test
    void testString() {
        //写入一条string数据
        redisTemplate.opsForValue().set("name","大笠");
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = "+name);
    }

    @Test
    void testSaveUser(){
        //写入数据
        redisTemplate.opsForValue().set("user:100",new User("大笠",18));
        //读取数据
        User user = (User)redisTemplate.opsForValue().get("user:100");
        System.out.println("user = "+user);
    }

}
