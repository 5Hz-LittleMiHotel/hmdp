package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import com.hmdp.constant.SessionConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.SMS_CODE_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate; // 因为构建项目时已经完成了对redis的配置,所以这里可以直接拿来用了

    @Override
    public Result sendCode(String phone, HttpSession session) { // Result是定义的表示结果的对象
        // 1.校验手机号,通过正则表达式校验手机号是否符合标准。
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3.符合，生成验证码 hutool工具类的radomutil
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码
//        session.setAttribute(SMS_CODE_KEY, code); // 保存到Session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES); // 保存到Redis(可能很多业务都这么做,所以要加个业务前缀以示区分)

        // 5.发送验证码(写个假的，但是可以用来做真的) TODO 写一个真的验证码
        log.debug("发送短信验证码成功："+code);

        // 6.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

//        // session版2. 校验验证码
//        Object CacheCode = session.getAttribute(SMS_CODE_KEY); // 最好不要使用魔法值"code"，可以新建一个常量类保存常量
//        String FrontCode = loginForm.getCode();
//        // 如果不一致报错
//        if(CacheCode == null || ! CacheCode.toString().equals(FrontCode)){ // 反向校验不需要if嵌套
//            // Session里没有验证码，说明之前就没点生成验证码
//            return Result.fail("验证码错误");
//        }

        // Redis版2.
        String CacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String FrontCode = loginForm.getCode();
        if(CacheCode == null || ! CacheCode.equals(FrontCode)){
            return Result.fail("验证码错误");
        }

        // 3. 查询手机号，判断是否存在：select * from tb_user where phone = ?
        // query()方法返回一条数据，是UserServiceImpl自带的，因为我们继承的ServiceImpl是mybatis-plus提供的。
        // 而实体类和mapper都已经告诉了（<UserMapper, User>，其中User类里有一个注解标明是tb_user）。
        // 因此query()方法就可以代替"select * from tb_user"。
        User user = query().eq("phone", phone).one();
        if(user == null){
            // 不存在，创建用户
            user = createUserWithPhone(phone);
        }

//        // session版4
//        // 将user类型转化为userdto类型,然后再存储到session中.目的是对数据脱敏
//         session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class)); // session版.hutool工具类的copy方法
//         return null; // 采用session登录不需要返回登陆凭证。

        // Redis版4
        // 4.1 随机生成token,作为登陆令牌
        String token = UUID.randomUUID().toString(true);// 使用UUID生成token.
        // 4.2 将User对象转为HashMap存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        // 4.3 存储到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY +token, userMap); // 报错java.lang.Long cannot be cast to java.lang.String: userMap来源于userDTO,其中的id是long类型,无法存入redis(因为stringredistemplate要求key和value都是string类型). 为什么不用RedisTemplete，前面基础篇讲了，存储过程序列化反序列化过程会出问题
        stringRedisTemplate.expire(LOGIN_USER_KEY +token, LOGIN_USER_TTL, TimeUnit.MINUTES); // 设置token有效期,多少分钟内无操作踢出redis
        // 4.4 返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
//        user.setNickName(RandomUtil.randomString("Nick",8));
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(8));
        save(user); // 依旧mybatis-plus
        return user;
    }
}
