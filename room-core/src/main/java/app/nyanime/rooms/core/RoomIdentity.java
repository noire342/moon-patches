// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.BIP340Signer;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/** Audited library primitives; a fresh in-memory signing identity for each session. */
public final class RoomIdentity implements AutoCloseable {
    private ECPrivateKeyParameters secret;
    public final String publicKey;
    private static final SecureRandom RANDOM = new SecureRandom();

    public RoomIdentity() {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(BIP340Signer.getDomain(), RANDOM));
        var pair = generator.generateKeyPair();
        secret = (ECPrivateKeyParameters) pair.getPrivate();
        publicKey = hex(((ECPublicKeyParameters) pair.getPublic()).getQ().normalize().getAffineXCoord().getEncoded());
    }

    public RoomIdentity(byte[] key) {
        secret = new ECPrivateKeyParameters(new BigInteger(1, key), BIP340Signer.getDomain());
        publicKey = hex(BIP340Signer.getDomain().getG().multiply(secret.getD()).normalize().getAffineXCoord().getEncoded());
    }

    public synchronized byte[] sign(byte[] hash) {
        if (secret == null) throw new IllegalStateException("Identity closed");
        BIP340Signer signer = new BIP340Signer();
        signer.init(true, secret);
        signer.update(hash, 0, hash.length);
        return signer.generateSignature();
    }

    public static boolean verify(String publicKey, byte[] hash, String signature) {
        try {
            var key = BIP340Signer.decodePublicKey(bytes(publicKey, 32));
            if (key == null) return false;
            BIP340Signer signer = new BIP340Signer();
            signer.init(false, key);
            signer.update(hash, 0, hash.length);
            return signer.verifySignature(bytes(signature, 64));
        } catch (RuntimeException ex) { return false; }
    }

    /** Domain-separated ECDH for the short-code admission channel, not a key derived from the code. */
    public synchronized byte[] admissionKey(String peer, String code) {
        if (secret == null) throw new IllegalStateException("Identity closed");
        ECPublicKeyParameters key = BIP340Signer.decodePublicKey(bytes(peer, 32));
        if (key == null || !code.matches("[0-9]{8}")) throw new IllegalArgumentException("Invalid admission peer");
        BigInteger d = secret.getD();
        ECPoint own = BIP340Signer.getDomain().getG().multiply(d).normalize();
        if (own.getAffineYCoord().toBigInteger().testBit(0)) d = BIP340Signer.getDomain().getN().subtract(d);
        byte[] x = key.getQ().multiply(d).normalize().getAffineXCoord().getEncoded();
        byte[] context = ("nya-rooms-admission-v2:" + code + ":" +
                (publicKey.compareTo(peer) < 0 ? publicKey + peer : peer + publicKey)).getBytes(StandardCharsets.UTF_8);
        try { return hash(concat(context, x)); } finally { Arrays.fill(x, (byte) 0); }
    }

    @Override public synchronized void close() { secret = null; }
    public static byte[] random(int size) { byte[] value = new byte[size]; RANDOM.nextBytes(value); return value; }
    public static byte[] hash(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
    public static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return result.toString();
    }
    public static byte[] bytes(String value, int size) {
        if (value == null || value.length() != size * 2 || !value.matches("[0-9a-f]+")) throw new IllegalArgumentException("Invalid key encoding");
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
