package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysOauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysOauthAccountRepository extends JpaRepository<SysOauthAccount, Long> {

    Optional<SysOauthAccount> findByProviderAndOpenId(String provider, String openId);

    Optional<SysOauthAccount> findByProviderAndUnionId(String provider, String unionId);
}
