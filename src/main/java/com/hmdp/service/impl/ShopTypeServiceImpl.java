package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
    public Result queryTypeList() {
        // 1. 定义一个固定的 Key（必须查询和写入一致）
        String key = "cache:shop:types";

        // 2. 从 Redis 中查询
        // range(key, 0, -1) 表示获取列表中所有的元素
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 3. 如果有，直接返回
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            // 这里的逻辑：把 List<String> 里的每一项转回 ShopType 对象
            List<ShopType> typeList = new ArrayList<>();
            for (String json : shopTypeJsonList) {
                ShopType type = JSONUtil.toBean(json, ShopType.class);
                typeList.add(type);
            }
            return Result.ok(typeList);
        }

        // 4. 如果没有，从数据库中查询（补全这一行）
        // list() 是 MyBatis Plus 提供的查询全部数据的方法
        List<ShopType> typeList = list();

        // 5. 数据库中没有，报错
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺列表为空!");
        }

        // 6. 数据库中有，保存至 Redis
        // 这里的逻辑：Redis 只能存字符串，所以要把对象一个个转成 JSON 字符串
        for (ShopType type : typeList) {
            String jsonStr = JSONUtil.toJsonStr(type);
            // 用 rightPush 从列表右侧一个一个塞进去
            stringRedisTemplate.opsForList().rightPush(key, jsonStr);
        }

        // 7. 返回结果
        return Result.ok(typeList);
    }
//    @Override
//    public Result queryTypeList() {
//
//        // 1. 从redis中查询
//        List<String> shopType = stringRedisTemplate.opsForList().range("id",0,-1);
//        // 2. 如果有,返回
//        if (shopType != null && !shopType.isEmpty()) {
//            List<ShopType> typeList = JSONUtil.toList(shopType.toString(), ShopType.class);
//            return Result.ok(typeList);
//        }
//        // 3. 如果没有,从数据库中查询
//        List<ShopType> typeList = ;
//        // 4. 数据库中没有,报错
//        if (typeList==null) {
//            return Result.fail("店铺列表为空!");
//        }
//        // 5. 数据库中有,保存至redis中
//        stringRedisTemplate.opsForList().set("shopTypeList", typeList.size(), typeList);
//        // 6. 返回结果
//        return Result.ok(typeList);
//    }
}
