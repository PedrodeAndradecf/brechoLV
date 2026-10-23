package Backend.BrechoAPI.service.impl;

import Backend.BrechoAPI.exception.StorageException;
import Backend.BrechoAPI.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class R2StorageService implements StorageService {

    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final String SUFIXO_S3_PRIVADO_R2 = ".r2.cloudflarestorage.com";

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrl;

    




















    public R2StorageService(S3Client s3Client,
                            @Value("${r2.bucket-name}") String bucketName,
                            @Value("${r2.public-url}") String publicUrl) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicUrl = guardaUrlPublica(publicUrl);
    }

    private static String guardaUrlPublica(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalStateException(
                    "r2.public-url está vazio — R2_PUBLIC_URL ausente cai no default vazio da linha"
                            + " ${R2_PUBLIC_URL:}, e a aplicação subiria para responder 201 no upload"
                            + " devolvendo uma URL sem host, inútil para quem abre a vitrine (falha"
                            + " calada). A aplicação recusa subir: defina R2_PUBLIC_URL com a URL"
                            + " pública do bucket");
        }
        if (publicUrl.contains("${")) {
            throw new IllegalStateException(
                    "r2.public-url=\"" + publicUrl + "\" é um placeholder não expandido — R2_PUBLIC_URL"
                            + " não foi resolvido pelo ambiente e o literal chegou ao campo. Como"
                            + " hasText(\"${...}\") é true, um guarda só de vazio o deixaria passar;"
                            + " por isso a aplicação"
                            + " recusa subir também com o literal. Defina R2_PUBLIC_URL no ambiente que"
                            + " sobe a aplicação");
        }
        if (hostEhS3PrivadoDoR2(publicUrl)) {
            throw new IllegalStateException(
                    "r2.public-url=\"" + publicUrl + "\" aponta para o endpoint S3 privado do R2"
                            + " (host *" + SUFIXO_S3_PRIVADO_R2 + "), que só responde a requisições"
                            + " assinadas: o navegador recebe 400 e a vitrine fica sem imagens. A"
                            + " aplicação recusa subir: defina R2_PUBLIC_URL com a URL pública do"
                            + " bucket (https://pub-<id>.r2.dev, ou um domínio próprio ligado a ele)");
        }
        return publicUrl;
    }

    











    private static boolean hostEhS3PrivadoDoR2(String publicUrl) {
        String host;
        try {
            host = new URI(publicUrl.trim()).getHost();
        } catch (URISyntaxException ilegivel) {
            return false;
        }
        return host != null && host.toLowerCase(Locale.ROOT).endsWith(SUFIXO_S3_PRIVADO_R2);
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        validate(file);

        
        
        String extension = extractExtension(file.getOriginalFilename());
        String storageKey = "%s/%s%s".formatted(folder, UUID.randomUUID(), extension);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException | RuntimeException e) {
            throw new StorageException("falha ao enviar a imagem", e);
        }

        return storageKey;
    }

    @Override
    public String buildPublicUrl(String storageKey) {
        return "%s/%s".formatted(publicUrl, storageKey);
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build());
        } catch (RuntimeException e) {
            throw new StorageException("falha ao remover a imagem", e);
        }
    }

    



    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("arquivo vazio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new StorageException("arquivo excede 5MB");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new StorageException("formato não suportado — use JPEG, PNG ou WebP");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
