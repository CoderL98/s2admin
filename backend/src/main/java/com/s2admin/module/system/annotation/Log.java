package com.s2admin.module.system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 标注在需要记录操作日志的 Controller 方法上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 操作模块,如:用户管理 */
    String module() default "";

    /** 操作类型,如:新增 / 修改 / 删除 / 查询 / 重置密码 */
    String operation() default "";
}
