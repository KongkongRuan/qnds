package com.qasky.qdns.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发现结果中的单台主机
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredHost {

    private String deviceIp;
    private String devicePort;
    private String protocol;
    private String sysName;
    private String sysDescr;
}
