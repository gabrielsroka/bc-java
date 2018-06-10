package org.bouncycastle.math.ec.rfc8032;

import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.math.ec.rfc7748.X25519Field;
import org.bouncycastle.math.raw.Nat256;
import org.bouncycastle.util.Arrays;

public abstract class Ed25519
{
    private static final long M28L = 0x0FFFFFFFL;
    private static final long M32L = 0xFFFFFFFFL;

    private static final int POINT_BYTES = 32;
    private static final int SCALAR_INTS = 8;
    private static final int SCALAR_BYTES = SCALAR_INTS * 4;

    public static final int PUBLIC_KEY_SIZE = POINT_BYTES;
    public static final int SECRET_KEY_SIZE = 32;
    public static final int SIGNATURE_SIZE = POINT_BYTES + SCALAR_BYTES;

//    private static final byte[] DOM2_PREFIX = Strings.toByteArray("SigEd25519 no Ed25519 collisions");

    private static final int[] P = new int[]{ 0xFFFFFFED, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x7FFFFFFF };
    private static final int[] L = new int[]{ 0x5CF5D3ED, 0x5812631A, 0xA2F79CD6, 0x14DEF9DE, 0x00000000, 0x00000000, 0x00000000, 0x10000000 };

    private static final int L0 = 0xFCF5D3ED;
    private static final int L1 = 0x012631A6;
    private static final int L2 = 0x079CD658;
    private static final int L3 = 0xFF9DEA2F;
    private static final int L4 = 0x000014DF;

    private static final int[] B_x = new int[]{ 0x0325D51A, 0x018B5823, 0x007B2C95, 0x0304A92D, 0x00D2598E, 0x01D6DC5C,
        0x01388C7F, 0x013FEC0A, 0x029E6B72, 0x0042D26D };    
    private static final int[] B_y = new int[]{ 0x02666658, 0x01999999, 0x00666666, 0x03333333, 0x00CCCCCC, 0x02666666,
        0x01999999, 0x00666666, 0x03333333, 0x00CCCCCC, };
    private static final int[] B_t_d2 = new int[]{ 0x037AAA68, 0x02448161, 0x0049EABC, 0x011E6556, 0x004DB3D0,
        0x0143598C, 0x02DF72F7, 0x005A85A1, 0x0344F863, 0x00DE22F6 };
    private static final int[] C_d = new int[]{ 0x035978A3, 0x02D37284, 0x018AB75E, 0x026A0A0E, 0x0000E014, 0x0379E898,
        0x01D01E5D, 0x01E738CC, 0x03715B7F, 0x00A406D9 };
    private static final int[] C_d2 = new int[]{ 0x02B2F159, 0x01A6E509, 0x01156EBD, 0x00D4141D, 0x0001C029, 0x02F3D130,
        0x03A03CBB, 0x01CE7198, 0x02E2B6FF, 0x00480DB3 };

    private static class PointXYTZ
    {
        int[] x = X25519Field.create();
        int[] y = X25519Field.create();
        int[] t = X25519Field.create();
        int[] z = X25519Field.create();
    }

    private static byte[] calculateS(byte[] r, byte[] k, byte[] s)
    {
        int[] t = new int[SCALAR_INTS * 2];     decodeScalar(r, 0, t);
        int[] u = new int[SCALAR_INTS];         decodeScalar(k, 0, u);
        int[] v = new int[SCALAR_INTS];         decodeScalar(s, 0, v);

        Nat256.mulAddTo(u, v, t);

        byte[] result = new byte[SCALAR_BYTES * 2];
        for (int i = 0; i < t.length; ++i)
        {
            encode32(t[i], result, i * 4);
        }
        return reduceScalar(result);
    }

    private static boolean checkFieldElementVar(byte[] fe)
    {
        int[] t = new int[8];
        decode32(fe, 0, t, 0, 8);
        return !Nat256.gte(t, P);
    }

    private static boolean checkScalarVar(byte[] s)
    {
        int[] n = new int[SCALAR_INTS];
        decodeScalar(s, 0, n);
        return !Nat256.gte(n, L);
    }

    private static int decode24(byte[] bs, int off)
    {
        int n = bs[  off] & 0xFF;
        n |= (bs[++off] & 0xFF) << 8;
        n |= (bs[++off] & 0xFF) << 16;
        return n;
    }

    private static int decode32(byte[] bs, int off)
    {
        int n = bs[off] & 0xFF;
        n |= (bs[++off] & 0xFF) << 8;
        n |= (bs[++off] & 0xFF) << 16;
        n |=  bs[++off]         << 24;
        return n;
    }

    private static void decode32(byte[] bs, int bsOff, int[] n, int nOff, int nLen)
    {
        for (int i = 0; i < nLen; ++i)
        {
            n[nOff + i] = decode32(bs, bsOff + i * 4);
        }
    }

    private static boolean decodePointVar(byte[] p, int pOff, PointXYTZ r)
    {
        byte[] py = Arrays.copyOfRange(p, pOff, pOff + POINT_BYTES);
        int x_0 = (py[POINT_BYTES - 1] & 0x80) >>> 7;
        py[POINT_BYTES - 1] &= 0x7F;

        if (!checkFieldElementVar(py))
        {
            return false;
        }

        X25519Field.decode(py, 0, r.y);

        int[] u = X25519Field.create();
        int[] v = X25519Field.create();

        X25519Field.sqr(r.y, u);
        X25519Field.mul(C_d, u, v);
        X25519Field.subOne(u);
        X25519Field.addOne(v);

        if (!X25519Field.sqrtRatioVar(u, v, r.x))
        {
            return false;
        }

        X25519Field.normalize(r.x);
        if (x_0 == 1 && X25519Field.isZeroVar(r.x))
        {
            return false;
        }

        if (x_0 != (r.x[0] & 1))
        {
            X25519Field.negate(r.x, r.x);
        }

        pointExtendXY(r);
        return true;
    }

    private static void decodeScalar(byte[] k, int kOff, int[] n)
    {
        decode32(k, kOff, n, 0, SCALAR_INTS);
    }

    private static void encode24(int n, byte[] bs, int off)
    {
        bs[  off] = (byte)(n       );
        bs[++off] = (byte)(n >>>  8);
        bs[++off] = (byte)(n >>> 16);
    }

    private static void encode32(int n, byte[] bs, int off)
    {
        bs[  off] = (byte)(n       );
        bs[++off] = (byte)(n >>>  8);
        bs[++off] = (byte)(n >>> 16);
        bs[++off] = (byte)(n >>> 24);
    }

    private static void encode56(long n, byte[] bs, int off)
    {
        encode32((int)n, bs, off);
        encode24((int)(n >>> 32), bs, off + 4);
    }

    private static void encodePoint(PointXYTZ p, byte[] r, int rOff)
    {
        int[] x = X25519Field.create();
        int[] y = X25519Field.create();

        X25519Field.inv(p.z, y);
        X25519Field.mul(p.x, y, x);
        X25519Field.mul(p.y, y, y);
        X25519Field.normalize(x);
        X25519Field.normalize(y);

        X25519Field.encode(y, r, rOff);
        r[rOff + POINT_BYTES - 1] |= ((x[0] & 1) << 7);
    }

    public static void generatePublicKey(byte[] sk, int skOff, byte[] pk, int pkOff)
    {
        // TODO Not currently constant-time (see use of ...Var methods)

        SHA512Digest d = new SHA512Digest();
        byte[] h = new byte[d.getDigestSize()];

        d.update(sk, skOff, SECRET_KEY_SIZE);
        d.doFinal(h, 0);

        byte[] s = new byte[SCALAR_BYTES];
        pruneScalar(h, 0, s);

        scalarMultBaseEncodedVar(s, pk, pkOff);
    }

    private static void implSignVar(SHA512Digest d, byte[] h, byte[] s, byte[] pk, int pkOff, byte[] m, int mOff, int mLen, byte[] sig, int sigOff)
    {
        d.update(h, SCALAR_BYTES, SCALAR_BYTES);
        d.update(m, mOff, mLen);
        d.doFinal(h, 0);

        byte[] r = reduceScalar(h);
        byte[] R = new byte[POINT_BYTES];
        scalarMultBaseEncodedVar(r, R, 0);

        d.update(R, 0, POINT_BYTES);
        d.update(pk, 0, POINT_BYTES);
        d.update(m, mOff, mLen);
        d.doFinal(h, 0);

        byte[] k = reduceScalar(h);
        byte[] S = calculateS(r, k, s);

        System.arraycopy(R, 0, sig, sigOff, POINT_BYTES);
        System.arraycopy(S, 0, sig, sigOff + POINT_BYTES, SCALAR_BYTES);
    }

    private static void pointAdd(PointXYTZ p, PointXYTZ r)
    {
        int[] A = X25519Field.create();
        int[] B = X25519Field.create();
        int[] C = X25519Field.create();
        int[] D = X25519Field.create();
        int[] E = X25519Field.create();
        int[] F = X25519Field.create();
        int[] G = X25519Field.create();
        int[] H = X25519Field.create();

        X25519Field.apm(r.y, r.x, B, A);
        X25519Field.apm(p.y, p.x, D, C);
        X25519Field.mul(A, C, A);
        X25519Field.mul(B, D, B);
        X25519Field.mul(r.t, p.t, C);
        X25519Field.mul(C, C_d2, C);
        X25519Field.mul(r.z, p.z, D);
        X25519Field.add(D, D, D);
        X25519Field.apm(B, A, H, E);
        X25519Field.apm(D, C, G, F);
        X25519Field.carry(G);
        X25519Field.mul(E, F, r.x);
        X25519Field.mul(G, H, r.y);
        X25519Field.mul(E, H, r.t);
        X25519Field.mul(F, G, r.z);
    }

    private static void pointAddBase(PointXYTZ r)
    {
        int[] A = X25519Field.create();
        int[] B = X25519Field.create();
        int[] C = X25519Field.create();
        int[] D = X25519Field.create();
        int[] E = X25519Field.create();
        int[] F = X25519Field.create();
        int[] G = X25519Field.create();
        int[] H = X25519Field.create();

        X25519Field.apm(r.y, r.x, B, A);
        X25519Field.apm(B_y, B_x, D, C);
        X25519Field.mul(A, C, A);
        X25519Field.mul(B, D, B);
        X25519Field.mul(r.t, B_t_d2, C);
        X25519Field.add(r.z, r.z, D);
        X25519Field.apm(B, A, H, E);
        X25519Field.apm(D, C, G, F);
        X25519Field.carry(G);
        X25519Field.mul(E, F, r.x);
        X25519Field.mul(G, H, r.y);
        X25519Field.mul(E, H, r.t);
        X25519Field.mul(F, G, r.z);
    }

    private static void pointDouble(PointXYTZ r)
    {
        int[] A = X25519Field.create();
        int[] B = X25519Field.create();
        int[] C = X25519Field.create();
        int[] E = X25519Field.create();
        int[] F = X25519Field.create();
        int[] G = X25519Field.create();
        int[] H = X25519Field.create();

        X25519Field.sqr(r.x, A);
        X25519Field.sqr(r.y, B);
        X25519Field.sqr(r.z, C);
        X25519Field.add(C, C, C);
        X25519Field.apm(A, B, H, G);
        X25519Field.add(r.x, r.y, E);
        X25519Field.sqr(E, E);
        X25519Field.sub(H, E, E);
        X25519Field.add(C, G, F);
        X25519Field.carry(F);
        X25519Field.mul(E, F, r.x);
        X25519Field.mul(G, H, r.y);
        X25519Field.mul(E, H, r.t);
        X25519Field.mul(F, G, r.z);
    }

    private static void pointExtendXY(PointXYTZ p)
    {
        X25519Field.mul(p.x, p.y, p.t);
        X25519Field.one(p.z);
    }

    public synchronized static void precompute()
    {
    }

    private static void pruneScalar(byte[] n, int nOff, byte[] r)
    {
        System.arraycopy(n, nOff, r, 0, SCALAR_BYTES);

        r[0] &= 0xF8;
        r[SCALAR_BYTES - 1] &= 0x7F;
        r[SCALAR_BYTES - 1] |= 0x40;
    }

    private static byte[] reduceScalar(byte[] n)
    {
        long x00 =  decode32(n,  0)       & M32L;   // x00:32/00
        long x01 = (decode24(n,  4) << 4) & M32L;   // x01:28/00
        long x02 =  decode32(n,  7)       & M32L;   // x02:32/00
        long x03 = (decode24(n, 11) << 4) & M32L;   // x03:28/00
        long x04 =  decode32(n, 14)       & M32L;   // x04:32/00
        long x05 = (decode24(n, 18) << 4) & M32L;   // x05:28/00
        long x06 =  decode32(n, 21)       & M32L;   // x06:32/00
        long x07 = (decode24(n, 25) << 4) & M32L;   // x07:28/00
        long x08 =  decode32(n, 28)       & M32L;   // x08:32/00
        long x09 = (decode24(n, 32) << 4) & M32L;   // x09:28/00
        long x10 =  decode32(n, 35)       & M32L;   // x10:32/00
        long x11 = (decode24(n, 39) << 4) & M32L;   // x11:28/00
        long x12 =  decode32(n, 42)       & M32L;   // x12:32/00
        long x13 = (decode24(n, 46) << 4) & M32L;   // x13:28/00
        long x14 =  decode32(n, 49)       & M32L;   // x14:32/00
        long x15 = (decode24(n, 53) << 4) & M32L;   // x15:28/00
        long x16 =  decode32(n, 56)       & M32L;   // x16:32/00
        long x17 = (decode24(n, 60) << 4) & M32L;   // x17:28/00
        long x18 =  n[63]                 & 0xFFL;  // x18:08/00
        long t;

//        x18 += (x17 >> 28); x17 &= M28L;
        x09 -= x18 * L0;                            // x09:35/28
        x10 -= x18 * L1;                            // x10:35/32
        x11 -= x18 * L2;                            // x11:35/28
        x12 -= x18 * L3;                            // x12:35/32
        x13 -= x18 * L4;                            // x13:28/21

        x17 += (x16 >> 28); x16 &= M28L;            // x17:28/00, x16:28/00
        x08 -= x17 * L0;                            // x08:55/32
        x09 -= x17 * L1;                            // x09:55/36
        x10 -= x17 * L2;                            // x10:55/36
        x11 -= x17 * L3;                            // x11:55/36
        x12 -= x17 * L4;                            // x12:41/36

//        x16 += (x15 >> 28); x15 &= M28L;
        x07 -= x16 * L0;                            // x07:55/28
        x08 -= x16 * L1;                            // x08:56/32
        x09 -= x16 * L2;                            // x09:56/36
        x10 -= x16 * L3;                            // x10:56/36
        x11 -= x16 * L4;                            // x11:55/42

        x15 += (x14 >> 28); x14 &= M28L;            // x15:28/00, x14:28/00
        x06 -= x15 * L0;                            // x06:55/32
        x07 -= x15 * L1;                            // x07:56/28
        x08 -= x15 * L2;                            // x08:57/00
        x09 -= x15 * L3;                            // x09:57/00
        x10 -= x15 * L4;                            // x10:56/42

//        x14 += (x13 >> 28); x13 &= M28L;
        x05 -= x14 * L0;                            // x05:55/28
        x06 -= x14 * L1;                            // x06:56/32
        x07 -= x14 * L2;                            // x07:57/00
        x08 -= x14 * L3;                            // x08:57/55
        x09 -= x14 * L4;                            // x09:57/41

        x13 += (x12 >> 28); x12 &= M28L;            // x13:28/22, x12:28/00
        x04 -= x13 * L0;                            // x04:55/50
        x05 -= x13 * L1;                            // x05:56/50
        x06 -= x13 * L2;                            // x06:57/00
        x07 -= x13 * L3;                            // x07:57/56
        x08 -= x13 * L4;                            // x08:57/56

        x12 += (x11 >> 28); x11 &= M28L;            // x12:29/00, x11:28/00
        x03 -= x12 * L0;                            // x03:56/28
        x04 -= x12 * L1;                            // x04:57/00
        x05 -= x12 * L2;                            // x05:57/50
        x06 -= x12 * L3;                            // x06:57/56
        x07 -= x12 * L4;                            // x07:58/00

        x11 += (x10 >> 28); x10 &= M28L;            // x11:29/14, x10:28/00
        x02 -= x11 * L0;                            // x02:56/42
        x03 -= x11 * L1;                            // x03:57/42
        x04 -= x11 * L2;                            // x04:58/00
        x05 -= x11 * L3;                            // x05:58/41
        x06 -= x11 * L4;                            // x06:58/00

        x10 += (x09 >> 28); x09 &= M28L;            // x10:30/00, x09:28/00
        x01 -= x10 * L0;                            // x01:57/28
        x02 -= x10 * L1;                            // x02:58/00
        x03 -= x10 * L2;                            // x03:58/42
        x04 -= x10 * L3;                            // x04:58/57
        x05 -= x10 * L4;                            // x05:58/44

        x01 += (x00 >> 28); x00 &= M28L;            // x01:57/28, x00:28/00
        x02 += (x01 >> 28); x01 &= M28L;            // x02:58/29, x01:28/00
        x03 += (x02 >> 28); x02 &= M28L;            // x03:58/43, x02:28/00
        x04 += (x03 >> 28); x03 &= M28L;            // x04:59/00, x03:28/00
        x05 += (x04 >> 28); x04 &= M28L;            // x05:58/45, x04:28/00
        x06 += (x05 >> 28); x05 &= M28L;            // x06:58/31, x05:28/00
        x07 += (x06 >> 28); x06 &= M28L;            // x07:58/31, x06:28/00
        x08 += (x07 >> 28); x07 &= M28L;            // x08:58/00, x07:28/00
        x09 += (x08 >> 28); x08 &= M28L;            // x09:30/28, x08:28/00

        t    = x09 >>> 63;
        x09 += t;                                   // x09:30/28

        x00 -= x09 * L0;
        x01 -= x09 * L1;
        x02 -= x09 * L2;
        x03 -= x09 * L3;
        x04 -= x09 * L4;

        x01 += (x00 >> 28); x00 &= M28L;
        x02 += (x01 >> 28); x01 &= M28L;
        x03 += (x02 >> 28); x02 &= M28L;
        x04 += (x03 >> 28); x03 &= M28L;
        x05 += (x04 >> 28); x04 &= M28L;
        x06 += (x05 >> 28); x05 &= M28L;
        x07 += (x06 >> 28); x06 &= M28L;
        x08 += (x07 >> 28); x07 &= M28L;
        x09  = (x08 >> 28); x08 &= M28L;

        x09 -= t;

//        assert x09 == 0L || x09 == -1L;

        x00 += x09 & L0;
        x01 += x09 & L1;
        x02 += x09 & L2;
        x03 += x09 & L3;
        x04 += x09 & L4;

        x01 += (x00 >> 28); x00 &= M28L;
        x02 += (x01 >> 28); x01 &= M28L;
        x03 += (x02 >> 28); x02 &= M28L;
        x04 += (x03 >> 28); x03 &= M28L;
        x05 += (x04 >> 28); x04 &= M28L;
        x06 += (x05 >> 28); x05 &= M28L;
        x07 += (x06 >> 28); x06 &= M28L;
        x08 += (x07 >> 28); x07 &= M28L;

        byte[] r = new byte[SCALAR_BYTES];
        encode56(x00 | (x01 << 28), r,  0);
        encode56(x02 | (x03 << 28), r,  7);
        encode56(x04 | (x05 << 28), r, 14);
        encode56(x06 | (x07 << 28), r, 21);
        encode32((int)x08,          r, 28);
        return r;
    }

    private static void scalarMultVar(int[] n, PointXYTZ p, PointXYTZ r)
    {
        X25519Field.zero(r.x);
        X25519Field.one(r.y);
        X25519Field.zero(r.t);
        X25519Field.one(r.z);

        for (int bit = 255; bit >= 0; --bit)
        {
            int word = bit >>> 5, shift = bit & 0x1F;
            int kt = (n[word] >>> shift) & 1;

            pointDouble(r);

            if (kt != 0)
            {
                pointAdd(p, r);
            }
        }
    }

    private static void scalarMultBaseVar(int[] n, PointXYTZ r)
    {
        X25519Field.zero(r.x);
        X25519Field.one(r.y);
        X25519Field.zero(r.t);
        X25519Field.one(r.z);

        for (int bit = 255; bit >= 0; --bit)
        {
            int word = bit >>> 5, shift = bit & 0x1F;
            int kt = (n[word] >>> shift) & 1;

            pointDouble(r);

            if (kt != 0)
            {
                pointAddBase(r);
            }
        }
    }

    private static void scalarMultBaseEncodedVar(byte[] k, byte[] r, int rOff)
    {
        int[] n = new int[SCALAR_INTS];
        decodeScalar(k, 0, n);

        PointXYTZ p = new PointXYTZ();
        scalarMultBaseVar(n, p);

        encodePoint(p, r, rOff);
    }

    public static void sign(byte[] sk, int skOff, byte[] m, int mOff, int mLen, byte[] sig, int sigOff)
    {
        // TODO Not currently constant-time (see use of ...Var methods)

        SHA512Digest d = new SHA512Digest();
        byte[] h = new byte[d.getDigestSize()];

        d.update(sk, skOff, SECRET_KEY_SIZE);
        d.doFinal(h, 0);

        byte[] s = new byte[SCALAR_BYTES];
        pruneScalar(h, 0, s);

        byte[] pk = new byte[POINT_BYTES];
        scalarMultBaseEncodedVar(s, pk, 0);

        implSignVar(d, h, s, pk, 0, m, mOff, mLen, sig, sigOff);
    }

    public static void sign(byte[] sk, int skOff, byte[] pk, int pkOff, byte[] m, int mOff, int mLen, byte[] sig, int sigOff)
    {
        // TODO Not currently constant-time (see use of ...Var methods)

        SHA512Digest d = new SHA512Digest();
        byte[] h = new byte[d.getDigestSize()];

        d.update(sk, skOff, SECRET_KEY_SIZE);
        d.doFinal(h, 0);

        byte[] s = new byte[SCALAR_BYTES];
        pruneScalar(h, 0, s);

        implSignVar(d, h, s, pk, pkOff, m, mOff, mLen, sig, sigOff);
    }

    public static boolean verify(byte[] sig, int sigOff, byte[] pk, int pkOff, byte[] m, int mOff, int mLen)
    {
        // TODO Not currently constant-time (see use of ...Var methods)

        PointXYTZ pA = new PointXYTZ();
        if (!decodePointVar(pk, pkOff, pA))
        {
            return false;
        }

        byte[] R = Arrays.copyOfRange(sig, sigOff, sigOff + POINT_BYTES);
        byte[] S = Arrays.copyOfRange(sig, sigOff + POINT_BYTES, sigOff + SIGNATURE_SIZE);

        if (!checkScalarVar(S))
        {
            return false;
        }

        PointXYTZ pR = new PointXYTZ();
        if (!decodePointVar(R, 0, pR))
        {
            return false;
        }

        SHA512Digest d = new SHA512Digest();
        byte[] h = new byte[d.getDigestSize()];

        d.update(R, 0, POINT_BYTES);
        d.update(pk, pkOff, POINT_BYTES);
        d.update(m, mOff, mLen);
        d.doFinal(h, 0);

        byte[] k = reduceScalar(h);

        byte[] lhs = new byte[POINT_BYTES];
        scalarMultBaseEncodedVar(S, lhs, 0);

        int[] n = new int[SCALAR_INTS];
        decodeScalar(k, 0, n);

        PointXYTZ p = new PointXYTZ();
        scalarMultVar(n, pA, p);
        pointAdd(pR, p);

        byte[] rhs = new byte[POINT_BYTES];
        encodePoint(p, rhs, 0);

        return Arrays.areEqual(lhs, rhs);
    }
}