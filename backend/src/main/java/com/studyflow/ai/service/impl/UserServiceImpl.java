package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.UserLoginDTO;
import com.studyflow.ai.dto.UserRegisterDTO;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.service.UserService;
import com.studyflow.ai.vo.LoginVO;
import com.studyflow.ai.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(UserRegisterDTO userRegisterDTO) {
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userRegisterDTO.getUsername()));
        if (exists) {
            throw new BusinessException(ResultCodeEnum.USERNAME_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(userRegisterDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setNickname(userRegisterDTO.getNickname());
        user.setAvatar(StringUtils.hasText(userRegisterDTO.getAvatar()) ? userRegisterDTO.getAvatar() : null);
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        return toUserInfo(user);
    }

    @Override
    public LoginVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userLoginDTO.getUsername())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.INVALID_CREDENTIALS);
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException(ResultCodeEnum.USER_DISABLED);
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .accessToken(token)
                .tokenType(jwtTokenProvider.getTokenPrefix().trim())
                .expiresIn(jwtTokenProvider.getExpireSeconds())
                .userInfo(toUserInfo(user))
                .build();
    }

    @Override
    public UserInfoVO getCurrentUserInfo() {
        Long userId = UserContext.getRequiredUserId();
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        return toUserInfo(getValidUserById(userId));
    }

    @Override
    public User getValidUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException(ResultCodeEnum.USER_DISABLED);
        }
        return user;
    }

    private UserInfoVO toUserInfo(User user) {
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .build();
    }
}
