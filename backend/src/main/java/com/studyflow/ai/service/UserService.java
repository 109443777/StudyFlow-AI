package com.studyflow.ai.service;

import com.studyflow.ai.dto.UserLoginDTO;
import com.studyflow.ai.dto.UserRegisterDTO;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.vo.LoginVO;
import com.studyflow.ai.vo.UserInfoVO;

public interface UserService {

    UserInfoVO register(UserRegisterDTO userRegisterDTO);

    LoginVO login(UserLoginDTO userLoginDTO);

    UserInfoVO getCurrentUserInfo();

    User getValidUserById(Long userId);
}
