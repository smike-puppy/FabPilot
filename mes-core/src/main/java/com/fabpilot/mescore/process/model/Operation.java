package com.fabpilot.mescore.process.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 可被多个 RouteStep 复用的工序定义。 */
@TableName("operation")
public class Operation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
