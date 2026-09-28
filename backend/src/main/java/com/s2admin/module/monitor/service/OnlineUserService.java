package com.s2admin.module.monitor.service;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.SessionService;
import com.s2admin.module.security.TokenBlacklistService;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final SessionService sessionService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SysUserRepository userRepository;
    private final DataScopeService dataScopeService;

    public List<Map<String, Object>> list() {
        Set<Long> visible = dataScopeService.visibleUserIds();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long userId : sessionService.onlineUserIds()) {
            if (visible != null && !visible.contains(userId)) {
                continue;
            }
            SysUser user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            List<SessionService.Device> devices = sessionService.list(userId);
            if (devices.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getId());
            row.put("username", user.getUsername());
            row.put("nickname", user.getNickname());
            row.put("deptName", user.getDeptName());
            row.put("deviceCount", devices.size());
            row.put("devices", devices.stream().map(device -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sid", device.sid());
                item.put("ip", device.ip());
                item.put("ua", device.ua());
                item.put("loginAt", device.iat());
                return item;
            }).toList());
            rows.add(row);
        }
        return rows;
    }

    public void kickUser(Long userId) {
        assertOther(userId);
        SysUser user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("用户不存在"));
        dataScopeService.assertCanAccessUser(user);
        tokenBlacklistService.invalidateUser(userId);
    }

    public void kickDevice(Long userId, String sid) {
        assertOther(userId);
        SysUser user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("用户不存在"));
        dataScopeService.assertCanAccessUser(user);
        sessionService.remove(userId, sid);
    }

    private void assertOther(Long userId) {
        if (userId != null && userId.equals(SecurityUtils.getUserId())) {
            throw new BusinessException("不能强退当前登录账号");
        }
    }
}
