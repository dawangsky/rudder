package com.rudder.server.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在 6～72 之间")
    private String password;

    /** 可选展示名；为空时用邮箱前缀。 */
    private String displayName;
}
