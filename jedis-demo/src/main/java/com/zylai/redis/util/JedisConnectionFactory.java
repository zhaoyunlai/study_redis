package com.zylai.redis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/15/20:27
 * @Description:
 */
public class JedisConnectionFactory {
    private static final JedisPool jedisPool;

    static{
        //配置连接池
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        //最大连接数
        poolConfig.setMaxTotal(8);
        //最大空闲连接
        poolConfig.setMaxIdle(8);
        //最小空闲连接
        poolConfig.setMinIdle(0);
        //最大等待时间
        poolConfig.setMaxWaitMillis(1000);
        //创建连接池对象
        jedisPool = new JedisPool(poolConfig,
                "82.157.140.177",6379,1000,"123321");

    }

    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
