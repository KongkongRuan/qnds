package com.qasky.qdns.snmp;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.snmp4j.security.AuthenticationProtocol;
import org.snmp4j.security.ByteArrayWindow;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

public class AuthSM3_OLD implements AuthenticationProtocol {

    public static final OID ID = new OID("1.3.6.1.4.1.62068.1.1");
    private static final int DIGEST_LENGTH = 32;
    private static final int AUTH_CODE_LENGTH = 12; // SNMPv3 标准截断为 12 字节

    @Override
    public OID getID() {
        return ID;
    }

    @Override
    public int getDigestLength() {
        return DIGEST_LENGTH;
    }

    @Override
    public int getAuthenticationCodeLength() {
        return AUTH_CODE_LENGTH;
    }

    // --- 补充：SecurityProtocol 接口要求的密钥长度方法 ---
    public int getMinKeyLength() {
        return DIGEST_LENGTH; // SM3 密钥长度 32 字节
    }

    @Override
    public int getMaxKeyLength() {
        return DIGEST_LENGTH; // SM3 密钥长度 32 字节
    }

    @Override
    public boolean isSupported() {
        // 因为我们依赖全局注册的 BouncyCastle，并且代码里直接 new 了 SM3Digest
        // 只要能编译运行到这里，就说明环境支持
        return true;
    }

    @Override
    public boolean authenticate(byte[] authenticationKey, byte[] message, int messageOffset, int messageLength, ByteArrayWindow mac) {
        // 计算 HMAC
        byte[] expectedMac = computeHmac(authenticationKey, message, messageOffset, messageLength);

        // SNMP4J 要求将计算出来的 MAC 写入到原始报文中的指定位置
        System.arraycopy(expectedMac, 0, message, mac.getOffset(), AUTH_CODE_LENGTH);
        return true;
    }

    @Override
    public boolean isAuthentic(byte[] authenticationKey, byte[] message, int messageOffset, int messageLength, ByteArrayWindow mac) {
        // 先保存报文中携带的 MAC
        byte[] origMac = new byte[AUTH_CODE_LENGTH];
        System.arraycopy(message, mac.getOffset(), origMac, 0, AUTH_CODE_LENGTH);

        // 将报文中的 MAC 区域清零（RFC 要求验证时该区域填 0）
        for (int i = 0; i < AUTH_CODE_LENGTH; i++) {
            message[mac.getOffset() + i] = 0;
        }

        // 计算我们自己的 HMAC
        byte[] expectedMac = computeHmac(authenticationKey, message, messageOffset, messageLength);

        // 恢复报文原来的 MAC
        System.arraycopy(origMac, 0, message, mac.getOffset(), AUTH_CODE_LENGTH);

        // 比较
        for (int i = 0; i < AUTH_CODE_LENGTH; i++) {
            if (origMac[i] != expectedMac[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public byte[] passwordToKey(OctetString passwordString, byte[] engineID) {
        byte[] passwordBytes = passwordString.getValue();
        SM3Digest digest = new SM3Digest();
        byte[] cp = new byte[64];
        byte[] buf = new byte[DIGEST_LENGTH];

        int passwordIndex = 0;
        int count = 0;

        // 1MB KDF 扩展
        while (count < 1048576) {
            for (int i = 0; i < 64; i++) {
                cp[i] = passwordBytes[passwordIndex++ % passwordBytes.length];
            }
            digest.update(cp, 0, 64);
            count += 64;
        }
        digest.doFinal(buf, 0);

        // 与 EngineID 绑定生成 Localized Key
        SM3Digest localizedDigest = new SM3Digest();
        localizedDigest.update(buf, 0, buf.length);
        localizedDigest.update(engineID, 0, engineID.length);
        localizedDigest.update(buf, 0, buf.length);

        byte[] localizedKey = new byte[DIGEST_LENGTH];
        localizedDigest.doFinal(localizedKey, 0);

        return localizedKey;
    }

    @Override
    public byte[] hash(byte[] data) {
        return hash(data, 0, data.length);
    }

    @Override
    public byte[] hash(byte[] data, int offset, int length) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, offset, length);
        byte[] result = new byte[DIGEST_LENGTH];
        digest.doFinal(result, 0);
        return result;
    }

    @Override
    public byte[] changeDelta(byte[] oldKey, byte[] newKey, byte[] random) {
        // 用于 SNMP SET 修改密码的进阶功能，一般不常用。需要时需按 RFC 3414 实现。
        throw new UnsupportedOperationException("SM3 Key change (changeDelta) is currently not supported.");
    }

    // 内部 HMAC 辅助方法
    private byte[] computeHmac(byte[] key, byte[] message, int offset, int length) {
        HMac hMac = new HMac(new SM3Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(message, offset, length);
        byte[] result = new byte[hMac.getMacSize()];
        hMac.doFinal(result, 0);
        return result;
    }
}