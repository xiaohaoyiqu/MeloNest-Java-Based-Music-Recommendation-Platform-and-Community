package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.PlaylistCollaborationService;
import com.haoran.music.vo.playlist.CollaboratorVO;
import com.haoran.music.vo.playlist.PlaylistOperationLogVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;





@RestController
@RequestMapping("/playlist/collab")
public class PlaylistCollaborationController {

    @Resource
    private PlaylistCollaborationService playlistCollaborationService;




    @ApiLog("开启歌单协作")
    @PostMapping("/{playlistId}/enable")
    public Result<Void> enableCollaboration(@PathVariable Long playlistId) {
        playlistCollaborationService.enableCollaboration(playlistId);
        return Result.success();
    }







    @ApiLog("关闭歌单协作")
    @PostMapping("/{playlistId}/disable")
    public Result<Void> disableCollaboration(@PathVariable Long playlistId) {
        playlistCollaborationService.disableCollaboration(playlistId);
        return Result.success();
    }




    @ApiLog("邀请歌单协作者")
    @PostMapping("/{playlistId}/invite")
    public Result<Void> inviteCollaborator(
            @PathVariable Long playlistId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "editor") String role) {
        playlistCollaborationService.inviteCollaborator(playlistId, userId, role);
        return Result.success();
    }




    @ApiLog("接受协作邀请")
    @PostMapping("/{playlistId}/accept")
    public Result<Void> acceptInvitation(@PathVariable Long playlistId) {
        playlistCollaborationService.acceptInvitation(playlistId);
        return Result.success();
    }




    @ApiLog("拒绝协作邀请")
    @PostMapping("/{playlistId}/decline")
    public Result<Void> declineInvitation(@PathVariable Long playlistId) {
        playlistCollaborationService.declineInvitation(playlistId);
        return Result.success();
    }




    @ApiLog("移除协作者")
    @DeleteMapping("/{playlistId}/collaborator/{userId}")
    public Result<Void> removeCollaborator(
            @PathVariable Long playlistId,
            @PathVariable Long userId) {
        playlistCollaborationService.removeCollaborator(playlistId, userId);
        return Result.success();
    }

    @ApiLog("退出歌单协作")
    @PostMapping("/{playlistId}/leave")
    public Result<Void> leaveCollaboration(@PathVariable Long playlistId) {
        playlistCollaborationService.leaveCollaboration(playlistId);
        return Result.success();
    }




    @ApiLog("获取协作者列表")
    @GetMapping("/{playlistId}/collaborators")
    public Result<List<CollaboratorVO>> getCollaborators(
            @PathVariable Long playlistId,
            @RequestAttribute(value = "userId", required = false) Long viewerId) {
        List<CollaboratorVO> result = playlistCollaborationService.getCollaborators(playlistId, viewerId);
        return Result.success(result);
    }




    @ApiLog("更新协作者权限")
    @PutMapping("/{playlistId}/collaborator/{userId}")
    public Result<Void> updateCollaboratorPermission(
            @PathVariable Long playlistId,
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> permissions) {
        Boolean canAdd = permissions.getOrDefault("canAdd", false);
        Boolean canRemove = permissions.getOrDefault("canRemove", false);
        Boolean canEdit = permissions.getOrDefault("canEdit", false);

        playlistCollaborationService.updateCollaboratorPermission(playlistId, userId, canAdd, canRemove, canEdit);
        return Result.success();
    }




    @ApiLog("获取歌单操作记录")
    @GetMapping("/{playlistId}/logs")
    public Result<PageResult<PlaylistOperationLogVO>> getOperationLogs(
            @PathVariable Long playlistId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestAttribute(value = "userId", required = false) Long viewerId) {
        PageResult<PlaylistOperationLogVO> result =
                playlistCollaborationService.getOperationLogs(playlistId, page, size, viewerId);
        return Result.success(result);
    }




    @ApiLog("获取歌单协作审计摘要")
    @GetMapping("/{playlistId}/audit-events")
    public Result<List<Map<String, Object>>> getAuditEvents(
            @PathVariable Long playlistId,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long viewerId) {
        return Result.success(playlistCollaborationService.getAuditEvents(playlistId, limit, viewerId));
    }




    @ApiLog("获取我的协作歌单")
    @GetMapping("/my")
    public Result<List> getMyCollaborativePlaylists() {
        List result = playlistCollaborationService.getMyCollaborativePlaylists();
        return Result.success(result);
    }




    @ApiLog("获取待处理的协作邀请")
    @GetMapping("/invitations/pending")
    public Result<List> getPendingInvitations() {
        List result = playlistCollaborationService.getPendingInvitations();
        return Result.success(result);
    }
}
