package com.gotogether.storage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gotogether.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class StorageViewControllerTest {

    @Mock private StorageService storageService;

    @Test
    void redirectsToAFreshlyPresignedGetUrlForTheGivenKey() {
        when(storageService.presignGetUrl("profile-photos/user-123/abc.png"))
                .thenReturn("https://signed.example.com/get?sig=xyz");

        StorageViewController controller = new StorageViewController(storageService);
        ResponseEntity<Void> response = controller.view("profile-photos/user-123/abc.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("https://signed.example.com/get?sig=xyz");
    }
}
