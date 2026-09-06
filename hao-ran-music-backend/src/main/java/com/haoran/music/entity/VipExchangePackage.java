


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vip_exchange_package")
public class VipExchangePackage extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageCode;
    private String packageName;
    private Integer vipLevel;
    private Integer vipDays;
    private Integer costPoints;
    private Integer ruleVersion;
    private Integer status;
    private Integer sortOrder;
}
