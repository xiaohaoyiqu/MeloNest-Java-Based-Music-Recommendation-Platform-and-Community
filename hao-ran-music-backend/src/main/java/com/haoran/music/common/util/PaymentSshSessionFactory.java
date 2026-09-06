package com.haoran.music.common.util;

import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.File;





@Component
public class PaymentSshSessionFactory {

    private final PaymentConfig paymentConfig;

    public PaymentSshSessionFactory(PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    public Session connect() throws Exception {
        PaymentConfig.Ssh ssh = paymentConfig.getSsh();
        File knownHosts = requiredFile(ssh.getKnownHostsPath(), "支付SSH known_hosts未配置或不存在");

        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts.getAbsolutePath());

        String privateKeyPath = ssh.getPrivateKeyPath();
        if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
            File privateKey = requiredFile(privateKeyPath, "支付SSH私钥不存在");
            jsch.addIdentity(privateKey.getAbsolutePath());
        }

        if (ssh.getHost() == null || ssh.getHost().trim().isEmpty()) {
            throw new BusinessException("支付SSH主机未配置");
        }
        Session session = jsch.getSession(ssh.getUser(), ssh.getHost(), ssh.getPort());
        if (ssh.getPassword() != null && !ssh.getPassword().isEmpty()) {
            session.setPassword(ssh.getPassword());
        }
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setTimeout(ssh.getReadTimeout());
        session.connect(ssh.getConnectTimeout());
        return session;
    }

    private File requiredFile(String path, String message) {
        if (path == null || path.trim().isEmpty()) {
            throw new BusinessException(message);
        }
        File file = new File(path.trim());
        if (!file.isFile()) {
            throw new BusinessException(message);
        }
        return file;
    }
}
