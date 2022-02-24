package com.ilyapiskunov.wateringapp;

import android.util.Log;

import java.util.zip.Checksum;

class CRCUtils {

    private static final int CRC8_PRESET_VALUE = 0xff;
    private static final int CRC8_POLYNOMIAL = 0x8c;

    public static int getCRC8(byte[] data, int length) {
        CRC8 crc8 = new CRC8(CRC8_POLYNOMIAL, CRC8_PRESET_VALUE);
        crc8.update(data, 0, length);
        //Log.i("CRC", "CRC = " + Long.toHexString(crc8.getValue()));
        return (int) crc8.getValue();
    }

    public static int getCRC8(byte[] data) {
        return getCRC8(data, data.length);
    }

    private static class CRC8 implements Checksum
    {
        private final short init;
        private final short[]   crcTable = new short[256];
        private short   value;

        /**
         * Construct a CRC8 specifying the polynomial and initial value.
         * @param polynomial Polynomial, typically one of the POLYNOMIAL_* constants.
         * @param init Initial value, typically either 0xff or zero.
         */
        public CRC8(int polynomial, int init)
        {
            this.value = this.init = (short) init;
            for (int dividend = 0; dividend < 256; dividend++)
            {
                int remainder = dividend ;//<< 8;
                for (int bit = 0; bit < 8; ++bit)
                    if ((remainder & 0x01) != 0)
                        remainder = (remainder >>> 1) ^ polynomial;
                    else
                        remainder >>>= 1;
                crcTable[dividend] = (short)remainder;
            }
        }

        @Override
        public void update(byte[] buffer, int offset, int len)
        {
            for (int i = 0; i < len; i++)
            {
                int data = buffer[offset+i] ^ value;
                value = (short)(crcTable[data & 0xff] ^ (value << 8));
            }
        }

        /**
         * Updates the current checksum with the specified array of bytes.
         * Equivalent to calling <code>update(buffer, 0, buffer.length)</code>.
         * @param buffer the byte array to update the checksum with
         */
        public void update(byte[] buffer)
        {
            update(buffer, 0, buffer.length);
        }

        @Override
        public void update(int b)
        {
            update(new byte[]{(byte)b}, 0, 1);
        }

        @Override
        public long getValue()
        {
            return value & 0xff;
        }

        @Override
        public void reset()
        {
            value = init;
        }

    }
}
