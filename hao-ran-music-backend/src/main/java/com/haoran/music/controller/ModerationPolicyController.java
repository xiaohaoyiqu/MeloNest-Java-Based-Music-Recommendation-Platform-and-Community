package com.haoran.music.controller;



import com.haoran.music.enums.UserRole;
import com.haoran.music.common.annotation.RequireRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.ModerationPolicy;
import com.haoran.music.service.ModerationPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

   
                      
                         
   
@Slf4j
@RestController
@RequestMapping("/admin/moderation-policy")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class ModerationPolicyController {

    @Autowired
    private ModerationPolicyService policyService;

       
                
       
    @PostMapping("/update")
    public Result<Void> updatePolicy(@Valid @RequestBody PolicyUpdateDTO dto) {
        log.info("event=moderation_policy_update_requested policyCode={}", dto.getPolicyCode());
        policyService.updatePolicy(
            dto.getPolicyCode(),
            dto.getPolicyName(),
            dto.getPolicyContent(),
            dto.getAffectScope(),
            dto.getReauditRequired()
        );
        return Result.success();
    }

       
             
       
    @GetMapping("/list")
    public Result<?> listPolicies() {
        return Result.success(policyService.list());
    }


       
                   
       
    @GetMapping("/{policyCode}")
    public Result<ModerationPolicy> getPolicyByCode(@PathVariable String policyCode) {
        ModerationPolicy policy = policyService.getOne(
            new LambdaQueryWrapper<ModerationPolicy>()
                .eq(ModerationPolicy::getPolicyCode, policyCode)
                .orderByDesc(ModerationPolicy::getEffectiveTime)
                .last("LIMIT 1")
        );
        if (policy == null) {
            return Result.error("规则不存在");
        }
        return Result.success(policy);
    }
       
              
       
    static class PolicyUpdateDTO {
        @NotBlank(message = "规则编码不能为空")
        @Size(max = 64, message = "规则编码不能超过64个字符")
        @Pattern(regexp = "[A-Za-z0-9_.:-]+", message = "规则编码格式不正确")
        private String policyCode;
        @NotBlank(message = "规则名称不能为空")
        @Size(max = 100, message = "规则名称不能超过100个字符")
        private String policyName;
        @NotBlank(message = "规则内容不能为空")
        @Size(max = 10000, message = "规则内容不能超过10000个字符")
        private String policyContent;
        @NotBlank(message = "影响范围不能为空")
        @Pattern(regexp = "all|creator|lyric|song|album", message = "影响范围不受支持")
        private String affectScope;
        @NotNull(message = "是否复审不能为空")
        private Boolean reauditRequired;

        public String getPolicyCode() { return policyCode; }
        public String getPolicyName() { return policyName; }
        public String getPolicyContent() { return policyContent; }
        public String getAffectScope() { return affectScope; }
        public Boolean getReauditRequired() { return reauditRequired; }
    }
}
