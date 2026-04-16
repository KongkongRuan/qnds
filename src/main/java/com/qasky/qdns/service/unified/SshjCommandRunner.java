package com.qasky.qdns.service.unified;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * 基于SSHJ的SSH命令执行器
 */
@Component
public class SshjCommandRunner implements SshCommandRunner {

    @Override
    public CommandResult execute(String host,
                                 int port,
                                 String username,
                                 String password,
                                 String command,
                                 int connectTimeoutMs,
                                 int executeTimeoutMs) throws Exception {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.setConnectTimeout(connectTimeoutMs);
        ssh.setTimeout(executeTimeoutMs);

        try {
            ssh.connect(host, port);
            ssh.authPassword(username, password);

            Session session = ssh.startSession();
            try {
                Session.Command cmd = session.exec(command);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = cmd.getInputStream().read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                cmd.join();
                return new CommandResult(cmd.getExitStatus(), baos.toString("UTF-8"));
            } finally {
                session.close();
            }
        } finally {
            if (ssh.isConnected()) {
                ssh.disconnect();
            }
            ssh.close();
        }
    }
}
