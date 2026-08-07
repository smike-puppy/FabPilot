package com.fabpilot.mescore.common.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 需要乐观锁和幂等保护的写命令公共请求数据。
 *
 * <p>具体业务 DTO 继承本类后，只需声明该操作独有的输入字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionedCommandRequestTO {

    /** 调用方最近一次读取到的资源版本，用于防止并发覆盖。 */
    @NotNull(message = "expectedVersion 不能为空")
    private Long expectedVersion;

    /** 调用方为本次业务意图生成的唯一键，用于防止重复执行。 */
    @NotBlank(message = "idempotencyKey 不能为空")
    private String idempotencyKey;

    /** 发起命令的操作人标识，用于生产履历审计。 */
    @NotBlank(message = "operatorId 不能为空")
    private String operatorId;
}