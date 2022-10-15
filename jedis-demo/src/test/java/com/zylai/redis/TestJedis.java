package com.zylai.redis;

import com.zylai.redis.util.JedisConnectionFactory;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/15/20:16
 * @Description:
 */
public class TestJedis {
    private static Jedis jedis;

    @BeforeAll
    static void setUp(){
        //建立连接
        //jedis = new Jedis("82.157.140.177",6379);
        jedis = JedisConnectionFactory.getJedis();
        //填写密码
        jedis.auth("123321");
        //选择数据库
        jedis.select(0);
    }

    @Test
    @DisplayName("测试字符串")
    void testString(){
        String result = jedis.set("name","虎哥");
        System.out.println("result:"+result);

        String name = jedis.get("name");
        System.out.println("name = "+name);
    }

    @Test
    void testHash(){
        jedis.hset("user:1","name","jack");
        jedis.hset("user:1","age","18");

        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);
    }

    @AfterAll
    static void tearDown(){
        if(jedis != null){
            //这个实际的实现时，如果这个连接时从连接池中拿出来的，就归还
            jedis.close();
        }
    }
}
