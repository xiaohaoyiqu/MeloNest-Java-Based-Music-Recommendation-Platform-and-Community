




package com.haoran.music.service;

import com.haoran.music.vo.friend.FriendRequestVO;
import com.haoran.music.vo.friend.FriendVO;
import com.haoran.music.vo.friend.FriendGroupVO;

import java.util.List;




public interface UserFriendService {








    boolean sendFriendRequest(Long userId, Long targetUserId);









    boolean handleFriendRequest(Long requestId, Long userId, Boolean approved);








    List<FriendVO> getFriendList(Long userId, Long groupId);







    List<FriendRequestVO> getFriendRequests(Long userId);








    boolean unfriend(Long userId, Long friendId);









    boolean setSpecialMark(Long userId, Long friendId, String specialMark);









    boolean setFriendGroup(Long userId, Long friendId, Long groupId);








    Long createFriendGroup(Long userId, String groupName);









    boolean setFriendRemark(Long userId, Long friendId, String remark);









    boolean blockFriend(Long userId, Long friendId, Boolean blocked);








    boolean isFriend(Long userId, Long targetUserId);








    boolean hasPendingFriendRequest(Long userId, Long targetUserId);








    boolean isSpecialFollow(Long userId, Long friendId);







    List<FriendVO> getSpecialFollows(Long userId);







    List<FriendVO> getMutualFriendsNotSpecial(Long userId);







    List<FriendGroupVO> getFriendGroups(Long userId);










    boolean deleteFriendGroup(Long userId, Long groupId);
}
