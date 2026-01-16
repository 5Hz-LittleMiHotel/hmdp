package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";

    // 提前读取文件:
    // DefaultRedisScript 是 RedisScript 接口的一个实现类。
    // 泛型 <Long> 明确了 Lua 脚本执行后的返回值类型，对应 Redis 返回的数字（如：1代表成功，0代表失败）。
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        // 静态代码块：在类加载时（也就是程序刚启动，还没人开始抢券时）就执行，保证脚本只加载一次。

        // 1. 实例化脚本对象
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 2. 设置脚本文件位置：
        // ClassPathResource 会去项目的 src/main/resources 目录下寻找 "unlock.lua" 文件。
        // 这样避免了在 Java 代码里写一长串双引号包裹的 Lua 字符串，方便后期维护 Lua 逻辑。
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 3. 设置脚本返回值类型：
        // 必须显式指定为 Long.class，这样 Spring 在调用 execute 方法时，
        // 才知道要把 Redis 传回来的数据自动转换成 Java 的 Long 对象，防止类型转换异常。
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
//        return success; // 直接这么返回会有一个自动拆箱的过程(success是包装类,而返回值要求是一般类型)
        return Boolean.TRUE.equals(success); // 自动拆箱可能会有空指针的安全风险,因此使用一个常量和success做判断
    }
    @Override
    public void unlock() {
        // 使用 stringRedisTemplate 的 execute 方法来执行 Lua 脚本。
        // 该方法能保证脚本在 Redis 服务端以原子方式运行，不受 Java 端 Full GC 的干扰。
        stringRedisTemplate.execute(
                // 1. 脚本对象：
                // 传入我们之前在静态代码块中初始化好的 UNLOCK_SCRIPT。
                // 这样 Redis 就不需要重新解析脚本，直接从内存读取，性能极高。
                UNLOCK_SCRIPT,
                // 2. Key 列表：
                // Collections.singletonList 会创建一个只包含一个元素的不可变 List。
                // 这里传入锁的具体名字（如 "lock:order:10"），对应 Lua 脚本中的 KEYS[1]。
                Collections.singletonList(KEY_PREFIX + name),
                // 3. 参数列表（Args）：
                // 传入当前线程的唯一标识（UUID前缀 + 线程ID）。
                // 这里传入的所有后续参数都会按顺序填充到 Lua 脚本中的 ARGV 数组，对应 ARGV[1]。
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

//    @Override
//    public void unlock() {
//        // 获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断标识是否一致
//        if (threadId.equals(id)) {
//            // 通过del删除锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
