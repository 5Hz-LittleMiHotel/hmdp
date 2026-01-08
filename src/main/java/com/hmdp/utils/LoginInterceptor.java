package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class LoginInterceptor implements HandlerInterceptor {

//    // 方法中的session改为redis后, 要在这个类中注入redistemplate. 不能使用autowired resource等注解, 只能使用构造函数来注入.
//    // 因为LoginInterceptor类的对象是手动new出来的, 不是通过component等注解创建的, 也就是说不是由spring创建的, 那么我们使用构造函数进行注入.
//    // 谁来帮我们注入呢? 谁用了它谁就注入. 那我们当初MvcConfig里面的拦截器用到它了, 打开MvcConfig看, new LoginInterceptor()报错了, 我们需要在这里注入.
//    // 那么这里怎么注入? 由于MvcCondig类加了@Configuration注解, 这个类由spring构建, 所以可以直接用@Resource注入, 获取stringRedisTemplate.
//    // 拿到stringRedisTemplate后, 放到new LoginInterceptor()中, 就好了. 这个技巧要学习一下.
//    private StringRedisTemplate stringRedisTemplate;
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override // controller的前置拦截
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

////        // 1. 获取Session
////        HttpSession session = request.getSession();
////        // 2. 获取Session中的用户
////        Object userObj = session.getAttribute("user");
////        // 3. 判断用户是否存
////        if (userObj == null){
////            // 4. 不存在，拦截
////            response.setStatus(401); //401未授权的意思
////            return false;
////        }
////        // 5. 存在，保存用户信息到ThreadLocal
////        // UserHolder这个类里面已经定义过了一个静态ThreadLocal常量,泛型是UserDTO类,也就是说专门处理user的.在其中定义了三种方法,用于将user存进threadlocal.
////        UserHolder.saveUser((UserDTO) userObj);
////        // 6. 放行
////        return true;
//
//
//        // 1. 获取请求头中的token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            // token不存在，拦截
//            response.setStatus(401); //401未授权的意思
//            return false;
//        }
//        // 2. 基于token获取redis中的用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        // 3. 判断用户是否存在
//        if (userMap.isEmpty()){
//            // 4. 不存在，拦截
//            response.setStatus(401); //401未授权的意思
//            return false;
//        }
//        // 5. 将查询到的HashMap转为UserDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false); // false:不忽略转换过程中的错误
//        // 6. 存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//        // 7. 刷新token有效期
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        // 8. 放行
//        return true;

        // refreshtokeninterceptor已经给了更新token的策略(对于所有请求,如果是用户就更新token,非用户也不拦截)
        // 因此,对于需要用户登录的业务,只需要判断是否需要拦截(threadlocal是否有用户,有就放行,没就拦截)
        if(UserHolder.getUser() == null){
            // 没有 需要拦截
            response.setStatus(401);
            return false;
        }
        // 有用户则放行
        return true;
    }

//    @Override // 渲染之后拦截，销毁用户信息
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        // 移除用户
//        UserHolder.removeUser();
//    }
}
// 写好拦截器后,无法生效.得在config里面写一个MvcConfig类,实现WebMvcConfigurer,代表一个Mvc相关的配置
