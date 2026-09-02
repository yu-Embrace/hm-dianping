package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送验证吗
     * @param phone
     * @param session
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断手机号是否正确
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合，返回错误信息
            return Result.fail("手机格式错误");
        }

        //3.符合，生成验证吗
        String code = RandomUtil.randomNumbers(6);
        //4.将验证码存入session
        session.setAttribute("code",code);
        //5.发送验证码
        log.debug("发送短信验证码成功：{}",code);
        //返回ok
        return Result.ok();
    }
}
