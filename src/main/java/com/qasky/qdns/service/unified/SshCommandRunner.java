package com.qasky.qdns.service.unified;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSH命令执行抽象，便于统一运维接口与测试复用。
 */
public interface SshCommandRunner {

    CommandResult execute(String host,
                          int port,
                          String username,
                          String password,
                          String command,
                          int connectTimeoutMs,
                          int executeTimeoutMs) throws Exception;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class CommandResult {
        private Integer exitStatus;
        private String output;
    }
}
