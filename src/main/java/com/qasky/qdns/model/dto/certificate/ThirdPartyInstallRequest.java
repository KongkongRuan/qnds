package com.qasky.qdns.model.dto.certificate;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 第三方证书安装请求。
 */
@Data
public class ThirdPartyInstallRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonAlias({"id"})
    private String deviceId;
    private String algorithm;
    private Boolean forceRegenerate;
    private String commonName;
    private String deviceCertPemBase64;
    private List<String> caChainPemBase64List = new ArrayList<String>();
}
