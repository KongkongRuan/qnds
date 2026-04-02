package com.qasky.qdns.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * OID定义
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OidDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String oid;
    private String name;
    private String valueType;
    private String descriptionZh;
    private boolean isTable;
    private String tableKey;
    /** null表示所有设备类型都支持 */
    private Set<String> deviceTypes;
    /** 是否可写(支持SET操作) */
    private boolean writable;
}
