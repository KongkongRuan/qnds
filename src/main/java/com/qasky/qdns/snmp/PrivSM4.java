package com.qasky.qdns.snmp;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.security.*;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.atomic.AtomicLong;

public class PrivSM4 extends PrivacyGeneric {
    private static final String PROTOCOL_CLASS = "SM4";

    private static final int BLOCK_SIZE = 16; // SM4块大小16字节
    private static final int KEY_LENGTH = 16; // SM4密钥长度16字节
    private static final int INIT_VECTOR_LENGTH = 16; // SM4初始向量长度(与块大小一致)
    private static final int DECRYPT_PARAMS_LENGTH = 8;

    public static final OID ID = new OID(new int[]{1, 3, 6, 1, 4, 1, 62068, 2, 1});

    private static final LogAdapter logger = LogFactory.getLogger(PrivSM4.class);

    private final boolean legacyMode;
    private final AtomicLong saltCounter = new AtomicLong(RandomUtil.randomLong());

    public PrivSM4(boolean legacyMode) {
        this.legacyMode = legacyMode;
        super.protocolId = legacyMode ? "SM4/CFB/PKCS5Padding" : "SM4/CFB/NoPadding";
        super.protocolClass = PROTOCOL_CLASS;
        super.initVectorLength = INIT_VECTOR_LENGTH;
        super.keyBytes = KEY_LENGTH;
        super.cipherPool = new CipherPool();
    }


    @Override
    public byte[] encrypt(byte[] unencryptedData, int offset, int length, byte[] encryptionKey, long engineBoots, long engineTime, DecryptParams decryptParams) {
        byte[] initVector = new byte[INIT_VECTOR_LENGTH];
        if (encryptionKey.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Needed key length is " + KEY_LENGTH + ". Got " + encryptionKey.length + ".");
        } else {
            if (decryptParams.array == null || decryptParams.length < 8) {
                decryptParams.array = new byte[DECRYPT_PARAMS_LENGTH];
            }

            decryptParams.length = DECRYPT_PARAMS_LENGTH;
            decryptParams.offset = 0;

            /* Set IV as engine_boots + engine_time + salt */
            initVector[0] = (byte) ((int) (engineBoots >> 24 & 255L));
            initVector[1] = (byte) ((int) (engineBoots >> 16 & 255L));
            initVector[2] = (byte) ((int) (engineBoots >> 8 & 255L));
            initVector[3] = (byte) ((int) (engineBoots & 255L));
            initVector[4] = (byte) ((int) (engineTime >> 24 & 255L));
            initVector[5] = (byte) ((int) (engineTime >> 16 & 255L));
            initVector[6] = (byte) ((int) (engineTime >> 8 & 255L));
            initVector[7] = (byte) ((int) (engineTime & 255L));

            long salt = legacyMode ? RandomUtil.randomLong() : saltCounter.getAndIncrement();
            for (int i = 56, j = 8; i >= 0; i -= 8, ++j) {
                initVector[j] = (byte) ((salt >> i) & 0xFF);
            }

            System.arraycopy(initVector, 8, decryptParams.array, 0, 8);
            if (logger.isDebugEnabled()) {
                logger.debug("initVector is " + HexUtil.encodeHexStr(initVector));
            }

            Cipher cipher = null;
            try {
                cipher = cipherPool.reuseCipher();
                if (cipher == null) {
                    cipher = Cipher.getInstance(protocolId, "BC");
                }
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "SM4"), new IvParameterSpec(initVector));
                byte[] encryptedData = cipher.doFinal(unencryptedData, offset, length);
                if (logger.isDebugEnabled()) {
                    byte[] logData = new byte[length];
                    System.arraycopy(unencryptedData, offset, logData, 0, length);
                    logger.debug("sm4 encrypt: Data to encrypt " + HexUtil.encodeHexStr(logData));
                    logger.debug("sm4 encrypt: used key " + HexUtil.encodeHexStr(encryptionKey));
                    logger.debug("sm4 encrypt: created privacy_params " + HexUtil.encodeHexStr(decryptParams.array));
                    logger.debug("sm4 encrypt: encrypted Data  " + HexUtil.encodeHexStr(encryptedData));
                }
                return encryptedData;
            } catch (Exception e) {
                logger.error("SM4 encrypt error", e);
                return null;
            } finally {
                if (cipher != null) {
                    cipherPool.offerCipher(cipher);
                }
            }
        }
    }

    @Override
    public byte[] decrypt(byte[] encryptedData, int offset, int length, byte[] decryptionKey, long engineBoots, long engineTime, DecryptParams decryptParams) {
        byte[] initVector = new byte[INIT_VECTOR_LENGTH];
        if (decryptionKey.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Needed key length is " + KEY_LENGTH + ". Got " + decryptionKey.length + ".");
        }

        /* Set IV as engine_boots + engine_time + decrypt params */
        initVector[0] = (byte) ( (engineBoots >> 24) & 0xFF);
        initVector[1] = (byte) ( (engineBoots >> 16) & 0xFF);
        initVector[2] = (byte) ( (engineBoots >> 8) & 0xFF);
        initVector[3] = (byte) ( (engineBoots) & 0xFF);
        initVector[4] = (byte) ( (engineTime >> 24) & 0xFF);
        initVector[5] = (byte) ( (engineTime >> 16) & 0xFF);
        initVector[6] = (byte) ( (engineTime >> 8) & 0xFF);
        initVector[7] = (byte) ( (engineTime) & 0xFF);
        System.arraycopy(decryptParams.array, decryptParams.offset, initVector, 8, 8);
        if (logger.isDebugEnabled()) {
            logger.debug("initVector is " + HexUtil.encodeHexStr(initVector));
        }

        Cipher cipher = null;
        try {
            cipher = cipherPool.reuseCipher();
            if (cipher == null) {
                cipher = Cipher.getInstance(protocolId, "BC");
            }
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptionKey, "SM4"), new IvParameterSpec(initVector));
            byte[] decryptedData = cipher.doFinal(encryptedData, offset, length);

            if (logger.isDebugEnabled()) {
                byte[] logData = new byte[length];
                System.arraycopy(encryptedData, offset, logData, 0, length);
                logger.debug("sm4 decrypt: Data to decrypt " + HexUtil.encodeHexStr(logData));
                logger.debug("sm4 decrypt: used key " + HexUtil.encodeHexStr(decryptionKey));
                logger.debug("sm4 decrypt: created privacy_params " + HexUtil.encodeHexStr(decryptParams.array));
                logger.debug("sm4 decrypt: decrypted Data  " + HexUtil.encodeHexStr(decryptedData));
            }

            return decryptedData;
        } catch (Exception e) {
            logger.error("SM4 decrypt error", e);
            return null;
        } finally {
            if (cipher != null) {
                cipherPool.offerCipher(cipher);
            }
        }
    }

    @Override
    public OID getID() {
        return (OID) ID.clone();
    }

    @Override
    public int getEncryptedLength(int scopedPDULength) {
        return legacyMode ? ((scopedPDULength % BLOCK_SIZE) + 1) * BLOCK_SIZE : scopedPDULength;
    }

    @Override
    public int getMinKeyLength() {
        return KEY_LENGTH;
    }

    @Override
    public int getMaxKeyLength() {
        return KEY_LENGTH;
    }

    @Override
    public int getDecryptParamsLength() {
        return DECRYPT_PARAMS_LENGTH;
    }

    @Override
    public byte[] extendShortKey(byte[] shortKey, OctetString password, byte[] engineID, AuthenticationProtocol authProtocol) {
        // we have to extend the key, currently only the AES draft
        // defines this algorithm, so this may have to be changed for other
        // privacy protocols
        byte[] extKey = new byte[getMinKeyLength()];
        int length = shortKey.length;
        System.arraycopy(shortKey, 0, extKey, 0, length);

        while (length < extKey.length)
        {
            byte[] hash = authProtocol.hash(extKey, 0, length);

            if (hash == null) {
                return null;
            }
            int bytesToCopy = extKey.length - length;
            if (bytesToCopy > authProtocol.getDigestLength()) {
                bytesToCopy = authProtocol.getDigestLength();
            }
            System.arraycopy(hash, 0, extKey, length, bytesToCopy);
            length += bytesToCopy;
        }
        return extKey;
    }
}
