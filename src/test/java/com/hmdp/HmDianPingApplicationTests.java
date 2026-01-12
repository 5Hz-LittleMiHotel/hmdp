package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);// 查询1号店铺
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test // 测试并发条件下生成id的性能
    void testIdWorker() throws InterruptedException {
        // 1. 创建计数器锁，初始值为300。目的是等待300个线程全部执行完。
        CountDownLatch latch = new CountDownLatch(300);

        // 2. 定义具体要执行的任务逻辑（Lambda表达式）
        Runnable task = () -> {
            // 每个线程循环执行100次生成ID的操作
            for (int i = 0; i < 100; i++) {
                // 调用redisIdWorker生成一个业务前缀为"order"的唯一ID
                long id = redisIdWorker.nextId("order");
                // 在控制台打印生成的ID（注意：高并发下IO打印会拖慢实际性能）
                System.out.println("id = " + id);
            }
            // 3. 当前线程的任务跑完了，计数器减 1
            latch.countDown();
        };

        // 4. 获取当前系统时间（毫秒），作为测试开始时间
        long begin = System.currentTimeMillis();

        // 5. 循环300次，将任务提交到线程池es中
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 6. 主线程在这里“刹车”阻塞，直到latch的计数器归零（即300个任务全部完成）
        latch.await();

        // 7. 获取当前系统时间，作为测试结束时间
        long end = System.currentTimeMillis();

        // 8. 打印总计耗时（结束时间 - 开始时间）
        System.out.println("time = " + (end - begin));
    }
}
