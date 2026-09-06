   
                      
                             
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.mapper.DecorationConfigMapper;
import com.haoran.music.service.DecorationConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

   
                
   
@Service
public class DecorationConfigServiceImpl extends ServiceImpl<DecorationConfigMapper, DecorationConfig>
        implements DecorationConfigService {

    @Override
    public DecorationConfig getByDecorationId(String decorationId) {
        LambdaQueryWrapper<DecorationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecorationConfig::getDecorationId, decorationId);
        return getOne(wrapper);
    }

    @Override
    public List<DecorationConfig> listByType(String decorationType) {
        LambdaQueryWrapper<DecorationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecorationConfig::getDecorationType, decorationType)
                .eq(DecorationConfig::getIsEnabled, 1)
                .orderByAsc(DecorationConfig::getSortOrder);
        return list(wrapper);
    }
}
