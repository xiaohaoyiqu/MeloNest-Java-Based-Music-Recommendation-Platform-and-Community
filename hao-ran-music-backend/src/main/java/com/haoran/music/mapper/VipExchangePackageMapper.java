   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.VipExchangePackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VipExchangePackageMapper extends BaseMapper<VipExchangePackage> {

    @Select("SELECT * FROM vip_exchange_package WHERE package_code = #{packageCode} AND status = 1 LIMIT 1")
    VipExchangePackage selectEnabledByCode(@Param("packageCode") String packageCode);

    @Select("SELECT * FROM vip_exchange_package WHERE status = 1 ORDER BY sort_order, id")
    List<VipExchangePackage> selectAllEnabled();
}
