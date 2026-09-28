package com.s2admin.module.system.controller;

import com.s2admin.module.common.Result;
import com.s2admin.module.system.repository.ErrorLogRepository;
import com.s2admin.module.system.repository.LoginLogRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.service.DataScopeService;
import com.s2admin.module.system.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 仪表盘统计,登录用户即可访问
 */
@RestController
@RequestMapping("/api/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final LoginLogRepository loginLogRepository;
    private final ErrorLogRepository errorLogRepository;
    private final DataScopeService dataScopeService;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        Set<Long> visible = dataScopeService.visibleUserIds();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        if (visible == null) {
            vo.setUserCount(userRepository.count());
            vo.setTodayLoginCount(loginLogRepository.countByLoginTimeGreaterThanEqual(startOfDay));
            vo.setErrorCount(errorLogRepository.count());
        } else if (visible.isEmpty()) {
            vo.setUserCount(0);
            vo.setTodayLoginCount(0);
            vo.setErrorCount(0);
        } else {
            vo.setUserCount(userRepository.countByIdIn(visible));
            vo.setTodayLoginCount(loginLogRepository.countByLoginTimeGreaterThanEqualAndUserIdIn(startOfDay, visible));
            vo.setErrorCount(errorLogRepository.countByUserIdIn(visible));
        }
        if (visible == null) {
            vo.setRoleCount(roleRepository.count());
        } else if (visible.isEmpty()) {
            vo.setRoleCount(0);
        } else {
            vo.setRoleCount(userRepository.countDistinctRolesByUserIdIn(visible));
        }
        return Result.success(vo);
    }
}
