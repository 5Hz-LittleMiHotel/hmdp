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
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire( Long id ) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) { // 这里逻辑和后面的queryWithMutex不一样
            // 3.未命中，直接返回(因为热点数据是人为控制的,未命中了才直接返回)
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功

        // 通常, 获取锁成功应再次检测redis缓存是否过期,做doublecheck.如果存在则无需重建缓存.
        // 为什么逻辑过期不存在等待抢锁的过程也要检查?
        // 因为在高并发的极限瞬间，可能发生这种事：
        //      线程 A 抢到锁，提交了任务给线程池，任务还没开始跑，或者跑了一半。
        //      线程 A 处理完了，释放了锁。
        //      但注意！线程池里的异步任务可能因为拥挤，还没把新数据写进 Redis。
        //      此时 线程 D 进来了，发现还是旧时间，抢锁成功（因为 A 释放了）。
        //      线程 D 又往线程池塞了一个重复的重建任务。
        // 通常逻辑过期的双重检查是为了防止线程池被重复的重建任务塞满。
        // 但是!!!!!!!!!
        // “线程 D 进来了，发现还是旧时间”的意思是，redis更新慢了一步，线程D发现是老数据；
        // 这里代码虽然已经通过“保证“锁的释放”是在“异步更新 Redis 成功”之后”来***消除***了.
        // 还有种情况,“缓存重建,A释放锁的一瞬间被另外一个已检查过redis的线程D拿到锁”:
        // 这个现象在并发编程中有一个专门的术语，叫做 “判定与执行的脱节”（Check-then-Act）。

        // 总的来说,还是加上doublecheck比较好.
        if (isLock){ // 使用线程池开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit( ()->{// 这是一个新线程的开始

                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L); // 这里设置很短是为了节目效果,实际中应该设置为30min
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return shop;
    }

    public Shop queryWithMutex(Long id){ // 互斥锁解决缓存击穿
        String key = CACHE_SHOP_KEY + id;

        // 1. 从Redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在,返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 命中(穿透):
        if (shopJson!=null) {
            return null;
        }
        // 4. 未命中(不存在真实的商户信息 or 防止穿透的空字符串),实现缓存重建
        // 4.1. 获取互斥锁
        String lockKey = "lock:shop:"+id;
        Shop shop;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2. 判断是否获取成功
            if (!isLock){
                // 4.3. 如果失败,休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4. 获取锁成功
            // 获取锁成功应再次检测redis缓存是否过期,做doublecheck.如果存在则无需重建缓存.
            // 风险： 假设线程 A 和线程 B 同时发现过期。A 先拿到锁，去重建了；
            //       B 稍微慢一点，在等锁或者刚准备拿锁。
            //       等 A 重建完释放锁后，B 拿到锁又去提交了一个重建任务。
            shop = getById(id);
            // 了发生高并发,这里休眠一下,模拟重建的延时
            Thread.sleep(200);
            // 5. 数据库中不存在,将一个空字符串写入redis,再返回错误.解决缓存穿透
            if (shop==null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6. 存在,写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }
        catch (InterruptedException e){
            throw new RuntimeException(e);
        }
        finally {
            // 7. 释放互斥锁
            unlock(lockKey);
        }
        // 8. 返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id){ // 封装仅仅解决缓存穿透的代码
        String key = CACHE_SHOP_KEY + id;
        // 1. 从Redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在,返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 如果里面是null\空值\换行符,isNotBlank(shopJson) 返回false.
        // 而空字符串是为了防止缓存穿透设置并存储在redis的,
        // 于是应先判断命中的是否是空值,如果是空字符串就报错,是空值就查询数据库.
        if (shopJson!=null) {
            return null;
        }
        // 4. 不存在对应信息(真实的商户信息 or 防止穿透的空字符串),查询数据库
        Shop shop = getById(id);
        // 5. 数据库中不存在,将一个空字符串写入redis,再返回错误
        if (shop==null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. 存在,写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. 返回
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); // 不要直接返回flag，因为拆箱的过程中有可能出现空指针。
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSecond) throws InterruptedException {
        // 1. 查询商铺数据
        Shop shop = getById(id);
        // 模拟重建缓存时有一定的延迟, 这里写个延时
        Thread.sleep(200);
        // 2. 封装逻辑时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond)); // 并不是设置TTL,而是我们设置的
        // 3. 写入Redis
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional // 如果删除缓存异常,数据库需要回滚,所以这里需要统一,引入事务
    public Object update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete("cache:shop:" + shop.getId());

        return Result.ok();
    }
}
