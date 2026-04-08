package com.studyflow.ai.controller;

import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.UserLoginDTO;
import com.studyflow.ai.dto.UserRegisterDTO;
import com.studyflow.ai.service.UserService;
import com.studyflow.ai.vo.LoginVO;
import com.studyflow.ai.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Register")
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        return Result.success(userService.register(userRegisterDTO));
    }

    @Operation(summary = "Login")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        return Result.success(userService.login(userLoginDTO));
    }
}
