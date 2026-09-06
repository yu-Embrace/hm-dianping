package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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
    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result qureyShopById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop =queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺信息不存在");
        }
        return Result.ok(shop);
    }

    private Shop queryWithMutex(Long id) {
        //1.从Redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue()
                .get(key);
        //2.存在，直接返回
        if (StrUtil.isNotBlank(shopJson)){
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 3.判断命中的是否是空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        // 4.实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);

            // 4.2 判断是否获取成功
            if(!isLock){
                //4.3 失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 成功，再次检测redis缓存是否存在
            if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(key))) {
                // 4.5 存在直接返回
                shop = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), Shop.class);
                return shop;
            }
            //5.不存在，查询数据库
            shop = getById(id);
            //模拟重建的时延
            Thread.sleep(200);
            //6.数据库中不存在，返回错误信息
            if (shop==null){
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key,""
                        ,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            //7.数据库中存在，写入redis缓存

            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop)
                    ,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 8.释放互斥锁
            unlock(lockKey);
        }
        //9.返回
        return shop;
    }

    private Shop queryWithPassThrough(Long id) {
        //1.从Redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue()
                .get(key);
        //2.存在，直接返回
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        //3.不存在，查询数据库
        Shop shop = getById(id);
        //4.数据库中不存在，返回错误信息
        if (shop==null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,""
                    ,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        //5.数据库中存在，写入redis缓存

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop)
                ,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.将商铺信息返回给前端
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        //为了防止空指针异常
        return BooleanUtil.isTrue(flag);
    }
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
    
    
    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("店铺不存在");
        }
        //1.更新数据库
        updateById(shop);

        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
