package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: Zhao YunLai
 * @Date: 2022/10/19/21:32
 * @Description:
 */
@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    //构造器注入
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *
     * @param key 缓存的key
     * @param value 缓存的value
     * @param ttlTime 过期时间
     * @param unit 过期时间单位
     */
    public void set(String key, Object value, Long ttlTime, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),ttlTime,unit);
    }

    /**
     * 设置逻辑过期时间
     * @param key 缓存的key
     * @param value 缓存的value
     * @param ttlTime 过期时间
     * @param unit 过期时间单位
     */
    public void setWithLogicalExpire(String key,Object value,Long ttlTime,TimeUnit unit){

        //1、设置redis对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttlTime)));
        //2、写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));

    }


    //解决缓存穿透的问题
    //function是一个函数，两个泛型第一个是参数类型，第二个是返回值类型

    /**
     * 查询，解决缓存穿透问题，就是查询一个缓存和数据库中没有存在的数据，避免请求都打到数据库
     * @param keyPrefix 缓存key值的前缀
     * @param id 查询的key的id
     * @param type 返回的数据类型Class对象
     * @param dbFallback 查询数据库的函数
     * @param ttlTime 过期时间
     * @param unit 过期时间单位
     * @param <R> 查询的返回值类型
     * @param <ID> key的id的类型
     * @return 查询结果
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type,
            Function<ID,R> dbFallback,
            Long ttlTime,TimeUnit unit){
        //1、从redis中查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3、存在、直接返回
            return  JSONUtil.toBean(json,type);
        }
        //这里命中了，走到这里就可能是空值，所以需要判断命中是否为空值
        if(json != null){
            //返回空
            return null;
        }

        //4、不存在、根据id查询数据库
        R r = dbFallback.apply(id);

        //5、不存在，返回错误。
        //为了避免出现缓存穿透问题，同时将这个空值写入到redis
        if(r == null){
            //避免缓存穿透问题，空值写入到redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回空值
            return null;
        }

        //6、存在，写入redis，同时设置超时时间
        this.set(key,r,ttlTime,unit);

        //7、返回
        return r;
    }


    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //尝试获取锁
    private boolean tryLock(String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }


    /***
     * 利用逻辑过期时间解决缓存击穿的问题
     * @param keyPrefix 缓存key值的前缀
     * @param id 查询的key的id
     * @param type 返回的数据类型Class对象
     * @param dbFallback 查询数据库的函数
     * @param ttlTime 过期时间
     * @param unit 过期时间单位
     * @param <R> 查询的返回值类型
     * @param <ID> key的id的类型
     * @return 查询结果
     */
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,
            Function<ID,R> dbFallback,
            Long ttlTime,TimeUnit unit){

        //1、从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if(StrUtil.isBlank(json)){
            //3、不存在、直接返回null
            return null;
        }
        //4、命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5、判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回
            return r;
        }

        //5.2 已经过期，需要缓存重建
        //6、缓存重建（一旦到这一步，不论是否能够获取到互斥锁，都会返回过期信息）
        //6.1、获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2、判断是否获取锁成功
        if(isLock){
            //获取成功之后应该再次检测redis缓存是否过期，如果未过期就不需要重建
            //6.3、成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    //1、查数据库
                    R apply = dbFallback.apply(id);
                    //2、写入redis
                    this.setWithLogicalExpire(key,apply,ttlTime,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.4、返回过期的商铺信息
        return r;
    }
}
