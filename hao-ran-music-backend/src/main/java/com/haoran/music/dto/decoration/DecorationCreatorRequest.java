   
                      
   

package com.haoran.music.dto.decoration;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class DecorationCreatorRequest {

    @NotBlank(message = "请给装饰起个名字")
    @Size(max = 80, message = "装饰名称不能超过80个字符")
    private String decorationName;

    @NotBlank(message = "请选择装饰类型")
    private String decorationType;

    @Size(max = 500, message = "装饰说明不能超过500个字符")
    private String description;

    @NotBlank(message = "请上传装饰图标")
    @Size(max = 500, message = "图标地址过长")
    private String iconUrl;

    @NotBlank(message = "请上传预览图")
    @Size(max = 500, message = "预览图地址过长")
    private String previewUrl;

    @Size(max = 4000, message = "样式配置不能超过4000个字符")
    private String styleConfig;

    private String rarity;
    private String obtainType;
    private Integer pointsCost;
    private Integer cashPrice;
    private Integer isPermanent;
    private Integer durationDays;
}
