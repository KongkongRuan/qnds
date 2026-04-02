package com.qasky.qdns.snmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.security.AuthenticationProtocol;
import org.snmp4j.security.DecryptParams;
import org.snmp4j.security.PrivacyProtocol;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class PrivSM4_OLD implements PrivacyProtocol {
    private static final Logger log = LoggerFactory.getLogger(PrivSM4_OLD.class);

    public static final OID ID = new OID("1.3.6.1.4.1.62068.2.1");
    private static final int MIN_KEY_LENGTH = 16;
    private static final int DECRYPT_PARAMS_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public OID getID() {
        return ID;
    }

    @Override
    public int getMinKeyLength() {
        return MIN_KEY_LENGTH;
    }

    @Override
    public int getMaxKeyLength() {
        return MIN_KEY_LENGTH;
    }

    @Override
    public int getDecryptParamsLength() {
        return DECRYPT_PARAMS_LENGTH;
    }

    @Override
    public int getEncryptedLength(int scopedPDULength) {
        return scopedPDULength; // CFB 模式密文长度等于明文长度
    }

    // --- 新增：实现 SecurityProtocol 接口的 isSupported 方法 ---
    @Override
    public boolean isSupported() {
        try {
            // 简单测试一下是否能获取到 BouncyCastle 的 SM4 实例
            Cipher.getInstance("SM4/CFB128/NoPadding", "BC");
            return true;
        } catch (Exception e) {
            log.error("检测到环境不支持 SM4: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] extendShortKey(byte[] shortKey, OctetString password, byte[] engineID, AuthenticationProtocol authProtocol) {
        // 保证返回长度为 16 字节的密钥
        if (shortKey.length == MIN_KEY_LENGTH) {
            return shortKey;
        }
        byte[] extKey = new byte[MIN_KEY_LENGTH];
        System.arraycopy(shortKey, 0, extKey, 0, Math.min(shortKey.length, MIN_KEY_LENGTH));
        return extKey;
    }

    @Override
    public byte[] encrypt(byte[] unencryptedData, int offset, int length, byte[] encryptKey,
                          long engineBoots, long engineTime, DecryptParams decryptParams) {
        try {
            byte[] iv = new byte[16];
            byte[] key = new byte[16];
            System.arraycopy(encryptKey, 0, key, 0, 16);

            // 1. 生成 8 字节随机盐值，并注入到 decryptParams 对象中发送给远端
            byte[] salt = new byte[8];
            secureRandom.nextBytes(salt);
            decryptParams.array = salt;
            decryptParams.offset = 0;
            decryptParams.length = 8;

            // 2. 构造 IV: engineBoots (4) + engineTime (4) + salt (8)
            iv[0] = (byte) ((engineBoots >> 24) & 0xFF);
            iv[1] = (byte) ((engineBoots >> 16) & 0xFF);
            iv[2] = (byte) ((engineBoots >> 8) & 0xFF);
            iv[3] = (byte) (engineBoots & 0xFF);
            iv[4] = (byte) ((engineTime >> 24) & 0xFF);
            iv[5] = (byte) ((engineTime >> 16) & 0xFF);
            iv[6] = (byte) ((engineTime >> 8) & 0xFF);
            iv[7] = (byte) (engineTime & 0xFF);
            System.arraycopy(salt, 0, iv, 8, 8);

            // 3. 执行加密
            Cipher cipher = Cipher.getInstance("SM4/CFB128/NoPadding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));

            return cipher.doFinal(unencryptedData, offset, length);
        } catch (Exception e) {
            log.error("SM4 加密失败", e);
            return null;
        }
    }

    @Override
    public byte[] decrypt(byte[] cryptedData, int offset, int length, byte[] decryptKey,
                          long engineBoots, long engineTime, DecryptParams decryptParams) {
        try {
            byte[] iv = new byte[16];
            byte[] key = new byte[16];
            System.arraycopy(decryptKey, 0, key, 0, 16);

            // 1. 从报文携带的 decryptParams 中提取盐值
            byte[] salt = new byte[8];
            System.arraycopy(decryptParams.array, decryptParams.offset, salt, 0, 8);

            // 2. 恢复 IV
            iv[0] = (byte) ((engineBoots >> 24) & 0xFF);
            iv[1] = (byte) ((engineBoots >> 16) & 0xFF);
            iv[2] = (byte) ((engineBoots >> 8) & 0xFF);
            iv[3] = (byte) (engineBoots & 0xFF);
            iv[4] = (byte) ((engineTime >> 24) & 0xFF);
            iv[5] = (byte) ((engineTime >> 16) & 0xFF);
            iv[6] = (byte) ((engineTime >> 8) & 0xFF);
            iv[7] = (byte) (engineTime & 0xFF);
            System.arraycopy(salt, 0, iv, 8, 8);

            // 3. 执行解密
            Cipher cipher = Cipher.getInstance("SM4/CFB128/NoPadding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));

            return cipher.doFinal(cryptedData, offset, length);
        } catch (Exception e) {
            log.error("SM4 解密失败", e);
            return null;
        }
    }
}