




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;




@Data
@TableName("message")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(value = "id", type = IdType.AUTO)
    private Long id;




    @TableField("sender_id")
    private Long senderId;




    @TableField("receiver_id")
    private Long receiverId;




    @TableField("message_type")
    private String messageType;




    @TableField("content")
    private String content;




    @TableField("resource_id")
    private Long resourceId;




    @TableField("resource_data")
    private String resourceData;




    @TableField("is_read")
    private Integer isRead;




    @TableField("is_recalled")
    private Integer isRecalled;




    @TableField("read_time")
    private LocalDateTime readTime;




    @TableField("is_deleted_by_sender")
    private Integer deletedBySender;




    @TableField("is_deleted_by_receiver")
    private Integer deletedByReceiver;




    @TableField("status")
    private String status;




    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;




    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;





    public Integer getIsDeletedBySender() {
        return deletedBySender;
    }




    public void setIsDeletedBySender(Integer deletedBySender) {
        this.deletedBySender = deletedBySender;
    }




    public Integer getIsDeletedByReceiver() {
        return deletedByReceiver;
    }




    public void setIsDeletedByReceiver(Integer deletedByReceiver) {
        this.deletedByReceiver = deletedByReceiver;
    }




    public Integer getIsDeleted() {
        return isDeleted;
    }




    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
