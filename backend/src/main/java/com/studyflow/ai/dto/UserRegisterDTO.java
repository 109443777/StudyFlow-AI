package com.studyflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotBlank(message = "username cannot be blank")
    @Size(min = 4, max = 32, message = "username length must be between 4 and 32")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username only supports letters, digits and underscore")
    private String username;

    @NotBlank(message = "password cannot be blank")
    @Size(min = 6, max = 64, message = "password length must be between 6 and 64")
    private String password;

    @NotBlank(message = "nickname cannot be blank")
    @Size(min = 2, max = 32, message = "nickname length must be between 2 and 32")
    private String nickname;

    @Size(max = 255, message = "avatar length must be less than 255")
    private String avatar;
}
