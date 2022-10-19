package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //查询，解决缓存穿透问题
        //Shop shop = cacheClient.queryWithPassThrough(
        //        CACHE_SHOP_KEY,id,Shop.class,
        //        this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期时间解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY,id,Shop.class,
                this::getById,
                //设置20L是为了测试
                20L,TimeUnit.SECONDS
                );
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //7、返回
        return Result.ok(shop);
    }

    ///**
    // * 使用逻辑过期解决缓存击穿问题
    // * @param id 商铺id
    // * @return 商铺
    // */
    //public Shop queryWithLogicalExpire(Long id){
    //    //1、从redis中查询商铺缓存
    //    String key = CACHE_SHOP_KEY + id;
    //    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //    //2、判断是否存在
    //    if(StrUtil.isBlank(shopJson)){
    //        //3、不存在、直接返回null
    //        return null;
    //    }
    //    //4、命中，需要先把json反序列化为对象
    //    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    //    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    //    LocalDateTime expireTime = redisData.getExpireTime();
    //    //5、判断是否过期
    //    if(expireTime.isAfter(LocalDateTime.now())){
    //        //5.1 未过期，直接返回店铺信息
    //        return shop;
    //    }
    //
    //    //5.2 已经过期，需要缓存重建
    //    //6、缓存重建（一旦到这一步，不论是否能够获取到互斥锁，都会返回过期信息）
    //    //6.1、获取互斥锁
    //    String lockKey = LOCK_SHOP_KEY + id;
    //    boolean isLock = tryLock(lockKey);
    //    //6.2、判断是否获取锁成功
    //    if(isLock){
    //        //获取成功之后应该再次检测redis缓存是否过期，如果未过期就不需要重建
    //        //6.3、成功，开启独立线程，实现缓存重建
    //        CACHE_REBUILD_EXECUTOR.submit(()->{
    //            try {
    //                //重建缓存
    //                this.saveShopRedis(id,20L);
    //            } catch (Exception e) {
    //                throw new RuntimeException(e);
    //            } finally {
    //                //释放锁
    //                unlock(lockKey);
    //            }
    //        });
    //    }
    //
    //    //6.4、返回过期的商铺信息
    //    return shop;
    //}
    //
    ////线程池
    //private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //
    ////提前将热点key存到redis，并设置逻辑过期时间
    //public void saveShopRedis(Long id,Long expireSeconds) throws InterruptedException {
    //    //1、查询店铺数据
    //    Shop shop = getById(id);
    //    //模拟延迟时间
    //    Thread.sleep(200);
    //    //2、封装逻辑过期时间
    //    RedisData redisData = new RedisData();
    //    redisData.setData(shop);
    //    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //    //3、写入redis
    //    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    //}
    //
    ////解决缓存击穿问题
    //public Shop queryWithMutex(Long id){
    //    //1、从redis中查询商铺缓存
    //    String key = CACHE_SHOP_KEY + id;
    //    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //    //2、判断是否存在
    //    if(StrUtil.isNotBlank(shopJson)){
    //        //3、存在、直接返回
    //        return  JSONUtil.toBean(shopJson, Shop.class);
    //    }
    //    //这里命中了，走到这里就可能是空值，所以需要判断命中是否为空值
    //    if(shopJson != null){
    //        //返回空
    //        return null;
    //    }
    //
    //    //4、实现缓存重建
    //    //4.1、获取互斥锁
    //    String lockKey = LOCK_SHOP_KEY + id;
    //    Shop shop = null;
    //    try {
    //        boolean isLock = tryLock(lockKey);
    //        //4.2、判断是否获取成功
    //        if(!isLock){
    //            //4.3、失败，则休眠并重试
    //            Thread.sleep(50);
    //            return queryWithMutex(id);
    //        }
    //
    //        //4.4、成功
    //        // 4.4.1、再次查询redis是否有值
    //        shopJson = stringRedisTemplate.opsForValue().get(key);
    //        //判断是否存在
    //        if(StrUtil.isNotBlank(shopJson)){
    //            //3、存在、直接返回
    //            return  JSONUtil.toBean(shopJson, Shop.class);
    //        }
    //        //这里命中了，走到这里就可能是空值，所以需要判断命中是否为空值
    //        if(shopJson != null){
    //            //返回空
    //            return null;
    //        }
    //
    //        //4.4.2、如果没有值再根据id查询数据库
    //        shop = getById(id);
    //        //模拟重建延迟
    //        Thread.sleep(200);
    //
    //        //5、不存在，返回错误。
    //        //为了避免出现缓存穿透问题，同时将这个空值写入到redis
    //        if(shop == null){
    //            //避免缓存穿透问题，空值写入到redis
    //            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
    //            //返回错误信息
    //            return null;
    //        }
    //
    //        //6、存在，写入redis，同时设置超时时间
    //        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //    } catch (InterruptedException e) {
    //        throw new RuntimeException(e);
    //    } finally {
    //        //7、释放互斥锁
    //        unlock(lockKey);
    //    }
    //    //8、返回
    //    return shop;
    //}
    //
    ////解决缓存穿透的问题
    //public Shop queryWithPassThrough(Long id){
    //    //1、从redis中查询商铺缓存
    //    String key = CACHE_SHOP_KEY + id;
    //    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //    //2、判断是否存在
    //    if(StrUtil.isNotBlank(shopJson)){
    //        //3、存在、直接返回
    //        return  JSONUtil.toBean(shopJson, Shop.class);
    //    }
    //    //这里命中了，走到这里就可能是空值，所以需要判断命中是否为空值
    //    if(shopJson != null){
    //        //返回错误信息
    //        return null;
    //    }
    //    //4、不存在、根据id查询数据库
    //    Shop shop = getById(id);
    //    //5、不存在，返回错误。
    //    //为了避免出现缓存穿透问题，同时将这个空值写入到redis
    //    if(shop == null){
    //        //避免缓存穿透问题，空值写入到redis
    //        stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
    //        //返回错误信息
    //        return null;
    //    }
    //
    //    //6、存在，写入redis，同时设置超时时间
    //    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //
    //    //7、返回
    //    return shop;
    //}
    ////尝试获取锁
    //private boolean tryLock(String lockKey){
    //    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
    //    return BooleanUtil.isTrue(flag);
    //}
    //
    ////释放锁
    //private void unlock(String lockKey){
    //    stringRedisTemplate.delete(lockKey);
    //}


    //更新数据
    @Override
    //通过事务控制原子性
    @Transactional
    public Result updateShop(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        //1、更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
