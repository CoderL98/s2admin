package com.s2admin.module.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户主体(实现 UserDetails)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    private String email;

    private String avatar;

    private Set<String> roles;

    private Set<String> permissions;

    /** 用户状态:0 正常 1 禁用 2 锁定 3 过期 */
    private Integer status;

    private Boolean mustChangePassword;

    /** 当前 AccessToken jti */
    private String jti;

    /** 登录会话 sid,access/refresh 共用,用于多端控制 */
    private String sid;

    public LoginUser(Long id, String username, String password, Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return !Integer.valueOf(3).equals(status);
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return !Integer.valueOf(2).equals(status);
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return Integer.valueOf(0).equals(status);
    }
}
