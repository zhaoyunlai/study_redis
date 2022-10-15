package com.zylai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zylai.reids.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
class RedisStringApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString() {
        //写入一条string数据
        stringRedisTemplate.opsForValue().set("name","大笠");
        Object name = stringRedisTemplate.opsForValue().get("name");
        System.out.println("name = "+name);
    }

    //json处理工具
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSaveUser() throws JsonProcessingException {
        //创建对象
        User dali = new User("大笠", 18);
        //序列化
        String json = objectMapper.writeValueAsString(dali);
        //写入数据
        stringRedisTemplate.opsForValue().set("user:200",json);

        //手动反序列化
        String jsonUser = stringRedisTemplate.opsForValue().get("user:200");
        User user = objectMapper.readValue(jsonUser, User.class);
        System.out.println("user = "+user);

    }


    @Test
    void testHash(){
        stringRedisTemplate.opsForHash().put("user:400","name","大笠啦");
        stringRedisTemplate.opsForHash().put("user:400","age","21");

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:400");
        System.out.println(entries);
    }

}
