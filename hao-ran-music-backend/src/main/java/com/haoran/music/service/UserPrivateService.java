




package com.haoran.music.service;

import com.haoran.music.entity.UserPrivate;





public interface UserPrivateService {







    UserPrivateDTO getUserPrivateInfo(Long userId);







    void saveUserPrivateInfo(Long userId, UserPrivateDTO dto);







    void saveRealName(Long userId, String realName);







    void saveIdCard(Long userId, String idCard);







    void savePhone(Long userId, String phone);







    String getPhone(Long userId);







    String getMaskedPhone(Long userId);







    String getIdCard(Long userId);









    boolean verifyRealName(Long userId, String realName, String idCard);








    void setRealNameVerified(Long userId, boolean verified, String verifyMethod);






    void deleteUserPrivate(Long userId);




    class UserPrivateDTO {
        private String realName;
        private String idCard;
        private String phone;
        private String email;
        private String provinceCode;
        private String cityCode;
        private String districtCode;
        private String addressDetail;
        private Boolean realNameVerified;
        private String bankName;
        private String bankAccount;


        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }

        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getProvinceCode() { return provinceCode; }
        public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }

        public String getCityCode() { return cityCode; }
        public void setCityCode(String cityCode) { this.cityCode = cityCode; }

        public String getDistrictCode() { return districtCode; }
        public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }

        public String getAddressDetail() { return addressDetail; }
        public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }

        public Boolean getRealNameVerified() { return realNameVerified; }
        public void setRealNameVerified(Boolean realNameVerified) { this.realNameVerified = realNameVerified; }

        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }

        public String getBankAccount() { return bankAccount; }
        public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    }
}
