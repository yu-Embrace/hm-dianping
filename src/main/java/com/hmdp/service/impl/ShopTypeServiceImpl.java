package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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


    @Override
    public Result queryShopType() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        //1.查询redis缓存中是否存在商铺类型
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.判断缓存是否命中
        if (CollUtil.isNotEmpty(shopTypeJsonList)){
            //3.命中，反序列化后直接返回
            List<ShopType> shopTypes = shopTypeJsonList.stream().map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        //4.未命中，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 5. 数据库中不存在，直接返回
        if (shopTypes.isEmpty()) {
            return Result.fail("商铺类型不存在！");
        }

        //6.写入redis缓存
        List<String> jsonList = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key,jsonList);
        stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}
