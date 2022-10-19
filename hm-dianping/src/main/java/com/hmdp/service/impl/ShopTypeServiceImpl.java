package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //查询商店类型
    @Override
    public Result queryTypeList() {
        //1、查缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        //2、存在，直接返回
        if(StrUtil.isNotBlank(shopTypeJson)){
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }

        //3、不存在，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        //4、不存在，返回错误信息
        if(shopTypes == null || shopTypes.size() < 1){
            return Result.fail("未查询到商铺列表");
        }
        //5、将数据库查询结果加入缓存
        shopTypeJson = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,shopTypeJson);

        //6、返回数据库查询结果
        return Result.ok(shopTypes);
    }
}
