package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/21/9:19
 * @Description:
 */
@Component
public class RedisIdWorker {

    //使用 2022/01/01 - 00:00:00 为起始的时间戳
    private static final long BEGIN_TIMESTAMP = 1643760000L;
    //序列号位数
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2、生成序列号，这里每个业务采用的key不一样，但是也不能让一个业务都使用同一个key，
        //因为redis中这个自增key是有上限的，64位，虽然很大了，但是还是有上限的
        //所以这里采用的策略是在业务后面再加上一个当前日期的时间戳（天）
        //这样不止保证了key足够充足，还可以根据这个key查看一天的订单量

        //2.1获取当前日期，精确到天
        //这里使用冒号作为分割，到时候可以统计月和年的信息
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 自增长
        //这里有警告说可能出现空指针，但是不可能出现的，因为如果key不存在，他会自己去创建这个key
        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);

        //3、拼接返回
        //位运算
        return timestamp << COUNT_BITS | count ;
    }
    public static void main(String[] args){
        LocalDateTime time = LocalDateTime.of(2022, 2, 2, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
