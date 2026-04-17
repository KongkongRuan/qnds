package com.qasky.qdns.model.dto.certificate;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serializable;

/**
 * 自建CA签发并下发请求。
 */
@Data
public class SelfCaIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonAlias({"id"})
    private String deviceId;
    private String algorithm;
    private Boolean forceRegenerate;
    private String commonName;
    private Integer validDays;
}
