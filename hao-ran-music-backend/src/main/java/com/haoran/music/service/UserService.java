package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.user.*;
import com.haoran.music.entity.User;
import com.haoran.music.vo.user.UserLoginVO;
import com.haoran.music.vo.user.UserVO;
import org.springframework.web.multipart.MultipartFile;





public interface UserService extends IService<User> {







    UserLoginVO login(UserLoginDTO dto);







    UserVO register(UserRegisterDTO dto);







    UserVO getCurrentUserInfo(Long userId);








    UserVO updateUserInfo(Long userId, UserUpdateDTO dto);








    String uploadAvatar(Long userId, MultipartFile file);







    UserVO getUserById(Long userId);







    IPage<UserVO> pageUsers(UserQueryDTO dto);









    Boolean changePassword(Long userId, String oldPassword, String newPassword);







    Boolean logout(Long userId);








    Boolean followUser(Long followerId, Long followeeId);








    Boolean unfollowUser(Long followerId, Long followeeId);









    IPage<UserVO> getFollowingList(Long userId, Integer page, Integer size);









    IPage<UserVO> getFollowersList(Long userId, Integer page, Integer size);








    Boolean isFollowing(Long followerId, Long followeeId);







    void sendVerifyCode(String phone, String type);






    void resetPassword(ResetPasswordDTO dto);







    void saveUserWallpapers(Long userId, String wallpapers);







    String getUserWallpapers(Long userId);







    void addWallpaper(Long userId, String wallpaper);







    void removeWallpaper(Long userId, String wallpaper);







    void setLocalMusicPath(Long userId, String path);







    String getLocalMusicPath(Long userId);






    void clearLocalMusicPath(Long userId);







    void deleteAccount(Long userId, String password);







    String refreshToken(String refreshToken);







    boolean validateToken(String token);









    Boolean addVipDays(Long userId, Integer days, String reason);









    void updateUserStatus(Long userId, Integer status);








    int batchUpdateUserStatus(java.util.List<Long> userIds, Integer status);







    void updateUserRole(Long userId, String role);








    int batchUpdateUserRole(java.util.List<Long> userIds, String role);






    void adminDeleteUser(Long userId);







    int batchDeleteUsers(java.util.List<Long> userIds);







    void adminResetPassword(Long userId, String newPassword);







    void adminEditUserInfo(Long userId, java.util.Map<String, Object> params);







    User getUserEntityById(Long userId);
}
