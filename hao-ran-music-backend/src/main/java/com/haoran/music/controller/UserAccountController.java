


package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.user.UserQueryDTO;
import com.haoran.music.dto.user.UserUpdateDTO;
import com.haoran.music.service.UserService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.enums.UserRole;
import com.haoran.music.vo.user.PublicUserVO;
import com.haoran.music.vo.user.UserVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;




@RestController
@RequestMapping("/user")
public class UserAccountController {

    @Resource
    private UserService userService;

    @Resource
    private UserVipService userVipService;

    @ApiLog("获取当前用户信息")
    @GetMapping("/current")
    public Result<UserVO> getCurrentUser(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        UserVO user = userService.getCurrentUserInfo(userId);
        user.setIsVip(Boolean.TRUE.equals(userVipService.isVip(userId)));
        return Result.success(user);
    }

    @ApiLog("上传用户头像")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.successData(userService.uploadAvatar(userId, file));
    }

    @ApiLog("更新用户信息")
    @PutMapping("/update")
    public Result<UserVO> updateUser(@RequestAttribute(value = "userId", required = false) Long userId,
                                     @Valid @RequestBody UserUpdateDTO dto) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(userService.updateUserInfo(userId, dto));
    }

    @ApiLog("获取用户信息")
    @GetMapping("/info/{id}")
    public Result<PublicUserVO> getUserById(@PathVariable("id") Long id) {
        Map<Long, LocalDateTime> vipExpirations = userVipService.getActiveVipExpirations(Collections.singleton(id));
        return Result.success(toPublicUser(userService.getUserById(id), vipExpirations));
    }

    @ApiLog("查询用户列表")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/page")
    public Result<IPage<UserVO>> pageUsers(UserQueryDTO dto) {
        IPage<UserVO> result = userService.pageUsers(dto);
        Map<Long, LocalDateTime> vipExpirations = getVipExpirations(result);
        result.getRecords().forEach(user -> user.setIsVip(vipExpirations.containsKey(user.getId())));
        return Result.success(result);
    }

    @ApiLog("搜索用户列表")
    @GetMapping("/list")
    public Result<IPage<PublicUserVO>> searchUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword(keyword);
        dto.setPage(page);
        dto.setSize(size);
        dto.setStatus(1);
        IPage<UserVO> result = userService.pageUsers(dto);
        Map<Long, LocalDateTime> vipExpirations = getVipExpirations(result);
        return Result.success(result.convert(user -> toPublicUser(user, vipExpirations)));
    }

    @ApiLog("修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestAttribute(value = "userId", required = false) Long userId,
                                       @RequestParam String oldPassword,
                                       @RequestParam String newPassword) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.changePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    @ApiLog("注销账户")
    @DeleteMapping("/delete-account")
    public Result<Void> deleteAccount(@RequestAttribute(value = "userId", required = false) Long userId,
                                      @RequestParam String password) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.deleteAccount(userId, password);
        return Result.success();
    }

    @ApiLog("获取用户壁纸")
    @GetMapping("/wallpapers")
    public Result<String> getUserWallpapers(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.successData(userService.getUserWallpapers(userId));
    }

    @ApiLog("保存用户壁纸")
    @PostMapping("/wallpapers")
    public Result<Void> saveUserWallpapers(@RequestAttribute(value = "userId", required = false) Long userId,
                                           @RequestBody String wallpapers) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.saveUserWallpapers(userId, wallpapers);
        return Result.success();
    }

    @ApiLog("添加壁纸")
    @PostMapping("/wallpaper/add")
    public Result<Void> addWallpaper(@RequestAttribute(value = "userId", required = false) Long userId,
                                     @RequestParam String wallpaper) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.addWallpaper(userId, wallpaper);
        return Result.success();
    }

    @ApiLog("删除壁纸")
    @DeleteMapping("/wallpaper/delete")
    public Result<Void> removeWallpaper(@RequestAttribute(value = "userId", required = false) Long userId,
                                        @RequestParam String wallpaper) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.removeWallpaper(userId, wallpaper);
        return Result.success();
    }

    @ApiLog("设置本地音乐路径")
    @PostMapping("/local-music-path")
    public Result<Void> setLocalMusicPath(@RequestAttribute(value = "userId", required = false) Long userId,
                                           @RequestParam String path) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.error(410, "本地乐库的门牌只留在这台设备，请回到本地音乐页重新选择文件夹");
    }

    @ApiLog("获取本地音乐路径")
    @GetMapping("/local-music-path")
    public Result<String> getLocalMusicPath(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.error(410, "本地乐库的门牌只留在这台设备，请回到本地音乐页重新选择文件夹");
    }

    @ApiLog("清空本地音乐路径")
    @DeleteMapping("/local-music-path")
    public Result<Void> clearLocalMusicPath(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userService.clearLocalMusicPath(userId);
        return Result.success();
    }

    private PublicUserVO toPublicUser(UserVO source, Map<Long, LocalDateTime> vipExpirations) {
        PublicUserVO target = new PublicUserVO();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setNickname(source.getNickname());
        target.setAvatar(source.getAvatar());
        target.setSignature(source.getSignature());
        target.setStatus(source.getStatus());
        target.setFansCount(source.getFansCount());
        target.setFollowingCount(source.getFollowingCount());
        target.setIsCreator(source.getIsCreator());
        target.setCreatorStatus(source.getCreatorStatus());
        target.setIsVip(vipExpirations.containsKey(source.getId()));
        target.setVerifiedInfo(source.getVerifiedInfo());
        target.setWorksCount(source.getWorksCount());
        return target;
    }

    private Map<Long, LocalDateTime> getVipExpirations(IPage<UserVO> page) {
        return userVipService.getActiveVipExpirations(page.getRecords().stream()
                .map(UserVO::getId)
                .collect(Collectors.toList()));
    }
}
