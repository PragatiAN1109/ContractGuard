package com.contractguard.api.consumer;

import com.contractguard.consumeranalysis.ConsumerSource;
import com.contractguard.consumeranalysis.ConsumerSourceService;
import com.contractguard.consumeranalysis.InvalidSourceBundleException;
import com.contractguard.consumeranalysis.JavaSourceBundle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/consumers")
public class ConsumerController {

    private final ConsumerSourceService consumerSourceService;

    public ConsumerController(ConsumerSourceService consumerSourceService) {
        this.consumerSourceService = consumerSourceService;
    }

    /**
     * Registers a consumer-source revision from uploaded Java files or a single .zip.
     *
     * Multipart rather than JSON so a browser can post real files without a client-side archiver.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<RegisteredConsumerResponse> register(
            @PathVariable UUID projectId,
            @RequestParam String serviceName,
            @RequestParam String consumesSchema,
            @RequestParam(required = false) String description,
            @RequestParam("files") MultipartFile[] files,
            UriComponentsBuilder uriBuilder) {

        JavaSourceBundle bundle = JavaSourceBundle.from(toUploads(files));
        ConsumerSource created = consumerSourceService.register(
                projectId, serviceName.trim(), consumesSchema.trim(), description, bundle);

        return ResponseEntity
                .created(uriBuilder.path("/api/v1/projects/{projectId}/consumers/{id}")
                        .build(projectId, created.getId()))
                .body(RegisteredConsumerResponse.from(created));
    }

    @GetMapping
    public List<RegisteredConsumerResponse> list(@PathVariable UUID projectId) {
        return consumerSourceService.findActive(projectId).stream()
                .map(RegisteredConsumerResponse::from)
                .toList();
    }

    @GetMapping("/{consumerId}")
    public RegisteredConsumerResponse get(@PathVariable UUID projectId, @PathVariable UUID consumerId) {
        return RegisteredConsumerResponse.from(consumerSourceService.getById(projectId, consumerId));
    }

    private static List<JavaSourceBundle.UploadedFile> toUploads(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new InvalidSourceBundleException("No source files were uploaded");
        }
        return Arrays.stream(files)
                .filter(file -> !file.isEmpty())
                .map(file -> {
                    try {
                        return new JavaSourceBundle.UploadedFile(
                                file.getOriginalFilename(), file.getBytes());
                    } catch (IOException e) {
                        throw new InvalidSourceBundleException(
                                "Could not read uploaded file " + file.getOriginalFilename());
                    }
                })
                .toList();
    }
}
