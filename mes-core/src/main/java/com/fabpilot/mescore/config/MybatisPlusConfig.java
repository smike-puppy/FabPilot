package com.fabpilot.mescore.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础配置。
 *
 * <p>当前仅注册乐观锁拦截器；分页、数据权限等插件按实际需求再引入，
 * 避免 MVP 过早增加全局查询行为。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 让带有 {@code @Version} 的 Lot、Equipment 更新携带版本条件。
     *
     * <p>若记录已被其他请求修改，更新会返回失败而不是覆盖对方的数据；
     * 写侧领域服务负责将该失败转换为可理解的并发冲突响应。</p>
     */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
