package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    // extends ServiceImpl：这是 MP 提供的基类。它像一个“万能工具箱”，里面塞满了 save、update、getById 等方法。

    // 泛型 <VoucherOrderMapper, VoucherOrder>：
    // 第一个参数 M (Mapper)：告诉 MP 使用哪个数据库操作接口。
    // 第二个参数 T (Entity)：告诉 MP 对应哪张表（通常 VoucherOrder 实体类上会有 @TableName 注解映射到数据库表）。

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }

        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        // 5.1. 一人一单(复制过来加锁用)
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1200); // 确定一个锁超时时间.跟业务执行时间有关.这里设1200是因为后面调试方便
        //加锁失败
        if (!isLock) { // 正常逻辑总用嵌套不优雅容易出问题,所以使用反向判断
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.1. 一人一单
        Long userId = UserHolder.getUser().getId(); // 使用登录拦截器取用户id
        // 5.1.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();        // 5.2.2 判断是否存在
        // 5.1.2 判断是否已经下过单
        if (count>0) {
            // 用户已经下过一次单
            return Result.fail("用户已经购买过一次!");
        }
        //5.2. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1") // update set stock = stock-1 from tb_seckill_voucher
                .eq("voucher_id", voucherId) // where id = ?
//                .eq("stock", voucher.getStock())// and stock = ? // 失败率高
                .gt("stock", 0)
                .update();
        // 第一个update()获取“修改执行器”,第二个update()是真正的“执行动作”
        if (!success) {
            return Result.fail("库存不足！");
        }

        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = redisIdWorker.nextId("order"); // 获取全局id
        voucherOrder.setId(orderId);
        // 6.2.用户id
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 程序发现 VoucherOrderServiceImpl 自己没写这个方法。
        // 于是向上找父类 ServiceImpl。
        // 父类里有从 IService 继承并实现的 save 逻辑。
        // 最终执行了 SQL 插入。
        return Result.ok(orderId);

//        synchronized(userId.toString().intern()){
//        // synchronized(userId.toString()){
//            // 我们期望是id值一样的情况下使用一把锁.但是每一个请求过来,userId都是一个全新的对象.因此对象变了锁就变了.
//            // 又因为我们要求的是userId值一样,所以这里用了tostring.然而,tostring并不能保证按照值来加锁.
//            // 其实tostring方法里面底层调用的是Long类的静态函数.在其内部其实是new了一个字符串.
//            // 那么这里即使同一个userId对象,使用tosrting也是生成了一个新的字符串对象.
//            // 所以调用一个字符串的方法,intern,返回字符串规范表示(也就是去字符串常量池里找一个跟这个值一样的字符串,把地址返回来).
//            // 也就是假如userId值是5,调用多少次tostring,new了多少次字符串,返回的值都是一样的.
//
//            // 5.1.1 查询订单
//            // 5.1.2 判断是否已经下过单
//            // ............
//            // return Result.ok(orderId);
//        }
//        // 然而,这里是先释放了锁,后提交了事务.
//        // 意味着其他线程可以进来了,但是事务没结束,没提交.
//        // 如果此时有别的线程进来查询订单,那我们新增的内容可能还没写入数据库!可能存在并发安全问题
//        // 所以synchronized应该加在调用函数时的地方,而不是函数里面.
    }
}
