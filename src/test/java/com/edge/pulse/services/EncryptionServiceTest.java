package com.edge.pulse.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // 64-char hex string = 32 bytes (AES-256 key)
        encryptionService = new EncryptionService("9e367ec1aaf5049a0d84c549cc33ecb3bb37dfc0f785e1af79e829e86f3aab17");
    }

    @Test
    void encryptDecrypt_roundTrip() {
        String plaintext = "This is sensitive feedback";
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(plaintext);

        assertThat(encrypted.ciphertext()).isNotEmpty();
        assertThat(encrypted.iv()).hasSize(12);

        String decrypted = encryptionService.decrypt(encrypted.ciphertext(), encrypted.iv());
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encryptDecrypt_differentInputs() {
        String text1 = "Hello";
        String text2 = "World";

        EncryptionService.EncryptedData enc1 = encryptionService.encrypt(text1);
        EncryptionService.EncryptedData enc2 = encryptionService.encrypt(text2);

        assertThat(enc1.ciphertext()).isNotEqualTo(enc2.ciphertext());

        assertThat(encryptionService.decrypt(enc1.ciphertext(), enc1.iv())).isEqualTo(text1);
        assertThat(encryptionService.decrypt(enc2.ciphertext(), enc2.iv())).isEqualTo(text2);
    }

    @Test
    void encrypt_nullInput_throwsException() {
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encrypt_emptyInput_throwsException() {
        assertThatThrownBy(() -> encryptionService.encrypt(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encryptDecrypt_unicodeText() {
        String arabic = "مرحبا بالعالم";
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(arabic);
        String decrypted = encryptionService.decrypt(encrypted.ciphertext(), encrypted.iv());
        assertThat(decrypted).isEqualTo(arabic);
    }
}
