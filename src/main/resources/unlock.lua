-- KEYS[1]: 锁的 key (例如 lock:order:10)
-- ARGV[1]: 当前线程的标识 (UUID + threadId)

-- 1. 获取锁中的线程标示
local id = redis.call('get', KEYS[1])

-- 2. 判断是否与指定的标示一致
if (id == ARGV[1]) then
    -- 3. 如果一致则释放锁（删除）
    return redis.call('del', KEYS[1])
end

-- 4. 如果不一致则什么都不做
return 0