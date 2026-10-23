package Backend.BrechoAPI.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;








@Configuration
public class R2Config {

    @Value("${r2.account-id}")
    private String accountId;

    @Value("${r2.access-key-id}")
    private String accessKeyId;

    @Value("${r2.secret-access-key}")
    private String secretAccessKey;

    
    
    @Value("${r2.endpoint:}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        String resolvedEndpoint = (endpoint == null || endpoint.isBlank())
                ? "https://%s.r2.cloudflarestorage.com".formatted(accountId)
                : endpoint;

        return S3Client.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
                .region(Region.of("auto"))   
                .forcePathStyle(true)        
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }
}
