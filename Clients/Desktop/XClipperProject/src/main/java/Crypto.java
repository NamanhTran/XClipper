/*


    CREDIT: https://stackoverflow.com/a/53015144/7942154


 */




import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Destroyable;

public class Crypto {

    private static final int AUTH_TAG_SIZE = 128; // bits

    // NIST recommendation: "For IVs, it is recommended that implementations
    // restrict support to the length of 96 bits, to
    // promote interoperability, efficiency, and simplicity of design."
    private static final int IV_LEN = 12; // bytes

    // number of random number bytes generated before re-seeding
    private static final double PRNG_RESEED_INTERVAL = Math.pow(2, 16);

    private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";

    private static final List<Integer> ALLOWED_KEY_SIZES = Arrays
            .asList(new Integer[] {128, 192, 256}); // bits

    private static SecureRandom prng;

    // Used to keep track of random number bytes generated by PRNG
    // (for the purpose of re-seeding)
    private static int bytesGenerated = 0;

    public byte[] encrypt(byte[] input, SecretKeySpec key) throws Exception {

        Objects.requireNonNull(input, "Input message cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (input.length == 0) {
            throw new IllegalArgumentException("Length of message cannot be 0");
        }

        if (!ALLOWED_KEY_SIZES.contains(key.getEncoded().length * 8)) {
            throw new IllegalArgumentException("Size of key must be 128, 192 or 256");
        }

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        byte[] iv = getIV(IV_LEN);

        GCMParameterSpec gcmParamSpec = new GCMParameterSpec(AUTH_TAG_SIZE, iv);

        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParamSpec);
        byte[] messageCipher = cipher.doFinal(input);

        // Prepend the IV with the message cipher
        byte[] cipherText = new byte[messageCipher.length + IV_LEN];
        System.arraycopy(iv, 0, cipherText, 0, IV_LEN);
        System.arraycopy(messageCipher, 0, cipherText, IV_LEN, messageCipher.length);
        return cipherText;
    }

    public byte[] decrypt(byte[] input, SecretKeySpec key) throws Exception {

        Objects.requireNonNull(input, "Input message cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (input.length == 0) {

            throw new IllegalArgumentException("Input array cannot be empty");
        }

        byte[] iv = new byte[IV_LEN];
        System.arraycopy(input, 0, iv, 0, IV_LEN);

        byte[] messageCipher = new byte[input.length - IV_LEN];
        System.arraycopy(input, IV_LEN, messageCipher, 0, input.length - IV_LEN);
        GCMParameterSpec gcmParamSpec = new GCMParameterSpec(AUTH_TAG_SIZE, iv);
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmParamSpec);
        return cipher.doFinal(messageCipher);
    }

    public byte[] getIV(int bytesNum) {

        if (bytesNum < 1) throw new IllegalArgumentException("Number of bytes must be greater than 0");

        byte[] iv = new byte[bytesNum];

        prng = Optional.ofNullable(prng).orElseGet(() -> {
            try {
                prng = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Wrong algorithm name", e);
            }
            return prng;
        });

        if (bytesGenerated > PRNG_RESEED_INTERVAL || bytesGenerated == 0) {
            prng.setSeed(prng.generateSeed(bytesNum));
            bytesGenerated = 0;
        }

        prng.nextBytes(iv);
        bytesGenerated = bytesGenerated + bytesNum;

        return iv;
    }

    static void clearSecret(Destroyable key) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        Field keyField = key.getClass().getDeclaredField("key");
        keyField.setAccessible(true);
        byte[] encodedKey = (byte[]) keyField.get(key);
        Arrays.fill(encodedKey, Byte.MIN_VALUE);
    }
}