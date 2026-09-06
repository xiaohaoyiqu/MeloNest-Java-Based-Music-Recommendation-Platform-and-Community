   
                      
                     
   

package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.service.MessageKeywordService;
import com.haoran.music.service.MessageService;
import com.haoran.music.dto.message.MessageBatchDeleteRequest;
import com.haoran.music.dto.message.MessageForwardRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

   
        
   
@Slf4j
@RestController
@RequestMapping("/message")
public class MessageController {

    private final MessageService messageService;
    private final MessageKeywordService messageKeywordService;

    public MessageController(MessageService messageService, MessageKeywordService messageKeywordService) {
        this.messageService = messageService;
        this.messageKeywordService = messageKeywordService;
    }

    private Long requireLogin(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "客官请先登录");
        }
        return userId;
    }

       
             
      
                                                  
                             
                   
       
    @PostMapping("/send/text")
    @ApiLog("发送文本消息")
    @RateLimit(maxRequests = 12, timeWindowSeconds = 60, operation = "sendPrivateText",
            message = "私信发送过于频繁，请稍后再试")
    public Result<Long> sendTextMessage(@RequestBody Map<String, Object> params,
                                        HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        String content = (String) params.get("content");

        Long messageId = messageService.sendTextMessage(senderId, receiverId, content);
        return Result.success(messageId);
    }

       
             
      
                                                    
                             
                   
       
    @PostMapping("/send/emoji")
    @ApiLog("发送表情消息")
    public Result<Long> sendEmojiMessage(@RequestBody Map<String, Object> params,
                                        HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        String emojiCode = (String) params.get("emojiCode");

        Long messageId = messageService.sendEmojiMessage(senderId, receiverId, emojiCode);
        return Result.success(messageId);
    }

       
             
      
                                                            
                             
                   
       
    @PostMapping("/image")
    @ApiLog("发送图片消息")
    public Result<Long> sendImageMessage(@RequestBody Map<String, Object> params,
                                        HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        Object assetIdValue = params.get("attachmentAssetId");
        if (assetIdValue == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "新图片消息必须提交attachmentAssetId");
        }
        Long attachmentAssetId;
        try {
            attachmentAssetId = Long.valueOf(String.valueOf(assetIdValue));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "attachmentAssetId不合法");
        }

        Long messageId = messageService.sendImageMessage(senderId, receiverId, attachmentAssetId);
        return Result.success(messageId);
    }

       
           
      
                                                           
                             
                   
       
    @PostMapping("/send/song")
    @ApiLog("分享歌曲")
    public Result<Long> sendSongMessage(@RequestBody Map<String, Object> params,
                                       HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        Long songId = Long.valueOf(params.get("songId").toString());
        String songData = (String) params.get("songData");

        Long messageId = messageService.sendSongMessage(senderId, receiverId, songId, songData);
        return Result.success(messageId);
    }

       
           
      
                                                             
                             
                   
       
    @PostMapping("/send/album")
    @ApiLog("分享专辑")
    public Result<Long> sendAlbumMessage(@RequestBody Map<String, Object> params,
                                        HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        Long albumId = Long.valueOf(params.get("albumId").toString());
        String albumData = (String) params.get("albumData");

        Long messageId = messageService.sendAlbumMessage(senderId, receiverId, albumId, albumData);
        return Result.success(messageId);
    }

       
            
       
    @PostMapping("/send/mv")
    @ApiLog("分享视频")
    public Result<Long> sendMvMessage(@RequestBody Map<String, Object> params,
                                      HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        Long mvId = Long.valueOf(params.get("mvId").toString());
        String mvData = (String) params.get("mvData");

        Long messageId = messageService.sendMvMessage(senderId, receiverId, mvId, mvData);
        return Result.success(messageId);
    }

       
           
      
                                                                   
                             
                   
       
    @PostMapping("/send/playlist")
    @ApiLog("分享歌单")
    public Result<Long> sendPlaylistMessage(@RequestBody Map<String, Object> params,
                                           HttpServletRequest request) {
        Long senderId = requireLogin(request);
        Long receiverId = Long.valueOf(params.get("receiverId").toString());
        Long playlistId = Long.valueOf(params.get("playlistId").toString());
        String playlistData = (String) params.get("playlistData");

        Long messageId = messageService.sendPlaylistMessage(senderId, receiverId, playlistId, playlistData);
        return Result.success(messageId);
    }

       
                   
      
                                
                           
                             
                              
                   
       
    @GetMapping("/chat/{otherUserId}")
    @ApiLog("获取聊天消息")
    public Result<IPage<Map<String, Object>>> getChatMessages(@PathVariable Long otherUserId,
                                                              @RequestParam(defaultValue = "1") Integer page,
                                                              @RequestParam(defaultValue = "20") Integer size,
                                                              HttpServletRequest request) {
        Long currentUserId = requireLogin(request);

        PageQuery pageQuery = new PageQuery(page, size);
        IPage<Map<String, Object>> messages = messageService.getChatMessageViews(currentUserId, otherUserId, pageQuery);

        return Result.success(messages);
    }

       
             
      
                            
                   
       
    @GetMapping("/conversations")
    @ApiLog("获取会话列表")
    public Result<List<Map<String, Object>>> getConversations(HttpServletRequest request) {
        Long userId = requireLogin(request);

        List<Map<String, Object>> conversations = messageService.getConversations(userId);

        return Result.success(conversations);
    }

       
              
      
                            
                             
                 
       
    @PostMapping("/read/{messageId}")
    @ApiLog("标记消息已读")
    public Result<Boolean> markAsRead(@PathVariable Long messageId,
                                     HttpServletRequest request) {
        Long userId = requireLogin(request);

        boolean success = messageService.markAsRead(messageId, userId);

        return Result.success(success);
    }

       
                      
      
                                
                              
                      
       
    @PostMapping("/read-all/{otherUserId}")
    @ApiLog("标记所有消息已读")
    public Result<Integer> markAllAsRead(@PathVariable Long otherUserId,
                                        HttpServletRequest request) {
        Long currentUserId = requireLogin(request);

        int count = messageService.markAllAsRead(currentUserId, otherUserId);

        return Result.success(count);
    }

       
           
      
                            
                             
                 
       
    @PostMapping("/recall/{messageId}")
    @ApiLog("撤回消息")
    public Result<Boolean> recallMessage(@PathVariable Long messageId,
                                        HttpServletRequest request) {
        Long userId = requireLogin(request);

        boolean success = messageService.recallMessage(messageId, userId);

        return success ? Result.success(true) : Result.error(400, "撤回失败，可能超过2分钟限制或无权限操作");
    }

       
           
      
                            
                             
                 
       
    @DeleteMapping("/{messageId}")
    @ApiLog("删除消息")
    public Result<Boolean> deleteMessage(@PathVariable Long messageId,
                                        HttpServletRequest request) {
        Long userId = requireLogin(request);

        boolean success = messageService.deleteMessage(messageId, userId);

        return Result.success(success);
    }

    @PostMapping("/batch-delete")
    @ApiLog("批量删除消息")
    public Result<Integer> batchDeleteMessages(@RequestBody @Valid MessageBatchDeleteRequest body,
                                               HttpServletRequest request) {
        return Result.success(messageService.batchDeleteMessages(body.getMessageIds(), requireLogin(request)));
    }

    @PostMapping("/forward")
    @ApiLog("转发消息")
    @RateLimit(maxRequests = 6, timeWindowSeconds = 60, operation = "forwardPrivateMessages",
            message = "消息转发过于频繁，请稍后再试")
    public Result<List<Long>> forwardMessages(@RequestBody @Valid MessageForwardRequest body,
                                              HttpServletRequest request) {
        return Result.success(messageService.forwardMessages(
                body.getMessageIds(), requireLogin(request), body.getReceiverId()));
    }

       
               
      
                            
                     
       
    @GetMapping("/unread/count")
    @ApiLog("获取未读消息数")
    public Result<Integer> getTotalUnreadCount(HttpServletRequest request) {
        Long userId = requireLogin(request);

        int count = messageService.getTotalUnreadCount(userId);

        return Result.success(count);
    }

       
                    
      
                                
                              
                     
       
    @GetMapping("/unread/count/{otherUserId}")
    @ApiLog("获取指定用户未读消息数")
    public Result<Integer> getUnreadCountFromUser(@PathVariable Long otherUserId,
                                                 HttpServletRequest request) {
        Long userId = requireLogin(request);

        int count = messageService.getUnreadCountFromUser(userId, otherUserId);

        return Result.success(count);
    }

       
                
      
                                
                             
                              
                 
       
    @PostMapping("/pin/{otherUserId}")
    @ApiLog("置顶会话")
    public Result<Boolean> setConversationPinned(@PathVariable Long otherUserId,
                                                 @RequestParam boolean pinned,
                                                 HttpServletRequest request) {
        Long currentUserId = requireLogin(request);

        boolean success = messageService.setConversationPinned(currentUserId, otherUserId, pinned);

        return Result.success(success);
    }

       
                  
      
                                
                             
                              
                 
       
    @PostMapping("/block/{otherUserId}")
    @ApiLog("屏蔽用户")
    public Result<Boolean> setUserBlocked(@PathVariable Long otherUserId,
                                         @RequestParam boolean blocked,
                                         HttpServletRequest request) {
        Long currentUserId = requireLogin(request);

        boolean success = messageService.setUserBlocked(currentUserId, otherUserId, blocked);

        return Result.success(success);
    }

       
                   
      
                                
                              
                      
       
    @DeleteMapping("/chat/{otherUserId}")
    @ApiLog("清空聊天记录")
    public Result<Integer> clearChatHistory(@PathVariable Long otherUserId,
                                           HttpServletRequest request) {
        Long currentUserId = requireLogin(request);

        int count = messageService.clearChatHistory(currentUserId, otherUserId);

        return Result.success(count);
    }

       
             
      
                            
                         
                           
                            
                   
       
    @GetMapping("/search")
    @ApiLog("搜索聊天消息")
    public Result<IPage<Map<String, Object>>> searchMessages(@RequestParam String keyword,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "20") Integer size,
                                                             HttpServletRequest request) {
        Long userId = requireLogin(request);

        PageQuery pageQuery = new PageQuery(page, size);
        IPage<Map<String, Object>> messages = messageService.searchMessageViews(userId, keyword, pageQuery);

        return Result.success(messages);
    }

                                                         

       
                   
      
                            
                       
       
    @GetMapping("/social/recommend/preference")
    @ApiLog("获取社交推荐偏好")
    public Result<MessageKeywordService.SocialRecommendPreference> getSocialRecommendPreference(HttpServletRequest request) {
        Long userId = requireLogin(request);

        MessageKeywordService.SocialRecommendPreference preference = messageKeywordService.getUserPreference(userId);

        return Result.success(preference);
    }

       
                 
      
                                            
                            
                 
       
    @PostMapping("/social/recommend/enabled")
    @ApiLog("设置社交推荐开关")
    public Result<Void> setSocialRecommendEnabled(@RequestBody Map<String, Boolean> params,
                                                  HttpServletRequest request) {
        Long userId = requireLogin(request);
        Boolean enabled = params.get("enabled");

        messageKeywordService.setSocialRecommendEnabled(userId, enabled != null ? enabled : false);

        return Result.success();
    }

       
                   
      
                             
                              
                 
       
    @PostMapping("/social/recommend/preference")
    @ApiLog("保存社交推荐偏好")
    public Result<Void> saveSocialRecommendPreference(@RequestBody MessageKeywordService.SocialRecommendPreference preference,
                                                      HttpServletRequest request) {
        Long userId = requireLogin(request);

        messageKeywordService.saveUserPreference(userId, preference);

        return Result.success();
    }

       
                 
      
                            
                 
       
    @DeleteMapping("/social/recommend/keywords")
    @ApiLog("清除社交推荐关键词")
    public Result<Void> clearUserKeywords(HttpServletRequest request) {
        Long userId = requireLogin(request);

        messageKeywordService.clearUserKeywords(userId);

        return Result.success();
    }
}
