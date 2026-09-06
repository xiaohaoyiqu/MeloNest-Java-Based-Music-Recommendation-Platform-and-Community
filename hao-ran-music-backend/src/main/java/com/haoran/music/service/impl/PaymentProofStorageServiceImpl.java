package com.haoran.music.service.impl;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.PaymentSshSessionFactory;
import com.haoran.music.service.PaymentProofStorageService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.stereotype.Service;






@Service
public class PaymentProofStorageServiceImpl implements PaymentProofStorageService {

    private static final String REFERENCE_PREFIX = "payment-proof:";

    private final PaymentSshSessionFactory sessionFactory;
    private final PaymentConfig paymentConfig;

    public PaymentProofStorageServiceImpl(PaymentSshSessionFactory sessionFactory,
                                          PaymentConfig paymentConfig) {
        this.sessionFactory = sessionFactory;
        this.paymentConfig = paymentConfig;
    }

    @Override
    public void delete(String proofReference) {
        String relativePath = resolveRelativePath(proofReference);
        String remoteFile = normalizeDirectory(paymentConfig.getProofPath()) + relativePath;
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = sessionFactory.connect();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(paymentConfig.getSsh().getConnectTimeout());
            channel.rm(remoteFile);
        } catch (SftpException exception) {
            if (exception.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw new IllegalStateException("付款凭证节点删除失败", exception);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("付款凭证节点连接失败", exception);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String resolveRelativePath(String reference) {
        if (reference == null || !reference.startsWith(REFERENCE_PREFIX)) {
            throw new BusinessException("付款凭证引用无效");
        }
        String relativePath = reference.substring(REFERENCE_PREFIX.length());
        if (!relativePath.matches("\\d{8}/\\d+/[A-Za-z0-9._-]+")) {
            throw new BusinessException("付款凭证引用无效");
        }
        return relativePath;
    }

    private String normalizeDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return "/sdb1/myprojoct/haoranmusic/song_requests/payment/proof/";
        }
        String normalized = directory.replace("\\", "/");
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
