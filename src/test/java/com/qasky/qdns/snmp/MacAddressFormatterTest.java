package com.qasky.qdns.snmp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MacAddressFormatterTest {

    @Test
    public void shouldFormatBinaryMacAddress() {
        assertEquals(
                "17:A4:08:D6:93:4A",
                MacAddressFormatter.format("non-printable", new byte[]{0x17, (byte) 0xA4, 0x08, (byte) 0xD6, (byte) 0x93, 0x4A})
        );
    }

    @Test
    public void shouldKeepAsciiMacAddressWithoutDoubleEncoding() {
        assertEquals(
                "17:A4:08:D6:93:4A",
                MacAddressFormatter.format("17:A4:08:D6:93:4A", "17:A4:08:D6:93:4A".getBytes())
        );
    }

    @Test
    public void shouldNormalizeCompactMacAddressText() {
        assertEquals(
                "17:A4:08:D6:93:4A",
                MacAddressFormatter.format("17a408d6934a", "17a408d6934a".getBytes())
        );
    }
}
