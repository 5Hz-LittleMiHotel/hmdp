package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker { // 基于redis的Id生成器
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1768176000L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32; // 序列号的位数

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 策略是基于redis进行自增长,不同业务都有一个不同的前缀key

        // Redis：负责“自增”并保住当前的“最高记录”。
        //  Java：负责“领走”号码，并拼接上时间戳变成最终的唯一 ID。

        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd")); // 用:隔开,方便更精确的统计
        // 2.2.自增长(用基本类型不用包装类,因为后面还要运算).这里飘黄是因为可能会出现空指针.实际不会,因为会自动创建key
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date); // increment自增函数,icr是自增的意思.
        // 每天都修改该业务的key,解决上限问题,方便更精确的统计,并为全局id提供时间维度:
        // (同一秒/同一天内，Redis 帮我们保证序列号不重复；而在不同天，由于 date 变了，即便序列号相同，拼接上前面的时间戳后，最终得到的 ID 依然是全球唯一的)

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }
//    public static void main(String[] args){
//        LocalDateTime time = LocalDateTime.of(2026, 1, 12, 0,0,0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }
}