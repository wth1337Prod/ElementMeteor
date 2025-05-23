package ru.elementcraft.elementmeteor.util;

import org.bukkit.configuration.file.FileConfiguration;
import ru.elementcraft.elementmeteor.ElementMeteor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.UUID;

public class SecurityManager {

    private final ElementMeteor plugin;
    private SecretKey secretKey;
    private byte[] salt;

    private static final String DEFAULT_PASSWORD = "password";
    private static final String SALT_KEY = "security.salt";
    private static final String ALGORITHM = "AES";

    public SecurityManager(ElementMeteor plugin) {
        this.plugin = plugin;

        initializeSecurity();
    }

    private void initializeSecurity() {
        FileConfiguration config = plugin.getConfig();

        String saltString = config.getString(SALT_KEY);
        if (saltString == null || saltString.isEmpty()) {
            generateSalt();
            config.set(SALT_KEY, Base64.getEncoder().encodeToString(salt));
            plugin.saveConfig();
        } else {
            try {
                salt = Base64.getDecoder().decode(saltString);
            } catch (IllegalArgumentException e) {
                generateSalt();
                config.set(SALT_KEY, Base64.getEncoder().encodeToString(salt));
                plugin.saveConfig();
            }
        }

        generateSecretKey();
    }

    private void generateSalt() {
        SecureRandom random = new SecureRandom();
        salt = new byte[16];
        random.nextBytes(salt);
    }

    private void generateSecretKey() {
        try {
            String serverUuid = UUID.randomUUID().toString();

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(serverUuid.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            secretKey = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
        }
    }

    public String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            return data;
        }
    }

    public String decrypt(String encryptedData) {
        try {
            if (isBase64(encryptedData)) {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                byte[] decodedData = Base64.getDecoder().decode(encryptedData);
                byte[] decryptedData = cipher.doFinal(decodedData);
                return new String(decryptedData, StandardCharsets.UTF_8);
            } else {
                return encryptedData;
            }
        } catch (Exception e) {
            return encryptedData;
        }
    }

    private boolean isBase64(String data) {
        try {
            Base64.getDecoder().decode(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String encryptDefaultPassword() {
        return encrypt(DEFAULT_PASSWORD);
    }
}
