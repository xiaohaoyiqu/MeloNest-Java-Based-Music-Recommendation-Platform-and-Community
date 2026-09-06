




package com.haoran.music.service;

import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.UserDecoration;

import java.util.List;




public interface DecorationService {







    List<UserDecoration> getUserDecorations(Long userId);








    List<UserDecoration> getUserDecorationsByType(Long userId, String decorationType);








    UserDecoration getEquippedDecoration(Long userId, String decorationType);








    boolean equipDecoration(Long userId, String decorationId);








    boolean unequipDecoration(Long userId, String decorationType);









    boolean grantDecoration(Long userId, String decorationId, String source);








    boolean redeemDecoration(Long userId, String decorationId);







    List<DecorationConfig> getDecorationConfigs(String decorationType);








    boolean hasDecoration(Long userId, String decorationId);




    class DecorationDTO {
        private Long configId;
        private String decorationId;
        private String decorationName;
        private String decorationType;
        private String description;
        private String iconUrl;
        private String previewUrl;
        private String styleConfig;
        private String rarity;
        private Boolean owned;
        private Boolean equipped;
        private Boolean canRedeem;
        private Integer pointsCost;
        private Integer cashPrice;
        private String obtainType;
        private Boolean permanent;
        private Integer durationDays;
        private String obtainDescription;


        public Long getConfigId() { return configId; }
        public void setConfigId(Long configId) { this.configId = configId; }

        public String getDecorationId() { return decorationId; }
        public void setDecorationId(String decorationId) { this.decorationId = decorationId; }

        public String getDecorationName() { return decorationName; }
        public void setDecorationName(String decorationName) { this.decorationName = decorationName; }

        public String getDecorationType() { return decorationType; }
        public void setDecorationType(String decorationType) { this.decorationType = decorationType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getIconUrl() { return iconUrl; }
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

        public String getPreviewUrl() { return previewUrl; }
        public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

        public String getStyleConfig() { return styleConfig; }
        public void setStyleConfig(String styleConfig) { this.styleConfig = styleConfig; }

        public String getRarity() { return rarity; }
        public void setRarity(String rarity) { this.rarity = rarity; }

        public Boolean getOwned() { return owned; }
        public void setOwned(Boolean owned) { this.owned = owned; }

        public Boolean getEquipped() { return equipped; }
        public void setEquipped(Boolean equipped) { this.equipped = equipped; }

        public Boolean getCanRedeem() { return canRedeem; }
        public void setCanRedeem(Boolean canRedeem) { this.canRedeem = canRedeem; }

        public Integer getPointsCost() { return pointsCost; }
        public void setPointsCost(Integer pointsCost) { this.pointsCost = pointsCost; }

        public Integer getCashPrice() { return cashPrice; }
        public void setCashPrice(Integer cashPrice) { this.cashPrice = cashPrice; }

        public String getObtainType() { return obtainType; }
        public void setObtainType(String obtainType) { this.obtainType = obtainType; }

        public Boolean getPermanent() { return permanent; }
        public void setPermanent(Boolean permanent) { this.permanent = permanent; }

        public Integer getDurationDays() { return durationDays; }
        public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

        public String getObtainDescription() { return obtainDescription; }
        public void setObtainDescription(String obtainDescription) { this.obtainDescription = obtainDescription; }
    }
}
