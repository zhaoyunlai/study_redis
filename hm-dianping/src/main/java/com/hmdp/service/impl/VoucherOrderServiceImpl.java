package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
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

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
        //    尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //3、判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
        //    秒杀结束
            return Result.fail("秒杀已经结束");
        }
        //4、判断库存是否充足
        if (voucher.getStock() < 1) {
        //    库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //先获取锁，再执行创建订单的操作，这里的锁是根据用户的id，一个用户的请求一把锁
        synchronized (userId.toString().intern()){
            //获取代理对象（和事务有关）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //创建订单
            //调用代理对象的方法
            return proxy.createVoucherOrder(voucherId);
        }
    }


    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        //5、一人一单
        Long userId = UserHolder.getUser().getId();
        //使用用户的id作为锁的值
        //5.1 查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2 判断是否存在
        if(count > 0){
            //    用户已经购买过了
            return Result.fail("用户已经购买过了");
        }

        //6、扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")//set语句
                //where条件
                .eq("voucher_id", voucherId)
                //乐观锁，cas方法
                //.eq("stock",voucher.getStock())
                //只要库存这个时候还大于0，就可以
                .gt("stock",0)
                .update();
        if(!success){
            return Result.fail("库存不足");
        }

        //7、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户id
        voucherOrder.setUserId(userId);
        //7.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //7.4保存订单
        save(voucherOrder);

        //返回订单
        return Result.ok(orderId);
    }
}
