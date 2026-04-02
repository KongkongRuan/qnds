package com.qasky.qdns.snmp;

import cn.hutool.crypto.digest.SM3;
import org.snmp4j.security.AuthGeneric;
import org.snmp4j.smi.OID;

public class AuthSM3 extends AuthGeneric {
    private static final int BLOCK_SIZE = 64; // SM3块大小64字节
    private static final int DIGEST_LENGTH = 32; // SM3哈希长度32字节

    public static final OID ID = new OID(new int[] { 1, 3, 6, 1, 4, 1, 62068, 1, 1 });

    public AuthSM3(boolean legacyMode) {
        super(SM3.ALGORITHM_NAME, DIGEST_LENGTH, legacyMode ? 32 : 12, BLOCK_SIZE);
    }

    @Override
    public OID getID() {
        return (OID) ID.clone();
    }
}
