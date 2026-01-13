package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
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
    @Override
    @Transactional
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
        //5，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1") // update set stock = stock-1 from xxx where xxx
                .eq("voucher_id", voucherId).update(); // where voucher_id = xxx
        // 第一个update()获取“修改执行器”,第二个update()是真正的“执行动作”
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = redisIdWorker.nextId("order"); // 获取全局id
        voucherOrder.setId(orderId);
        // 6.2.用户id
        Long userId = UserHolder.getUser().getId(); // 登录拦截器取用户id
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 程序发现 VoucherOrderServiceImpl 自己没写这个方法。
        // 于是向上找父类 ServiceImpl。
        // 父类里有从 IService 继承并实现的 save 逻辑。
        // 最终执行了 SQL 插入。

        return Result.ok(orderId);

    }
}
