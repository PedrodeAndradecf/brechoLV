package Backend.BrechoAPI.service;

import org.springframework.web.multipart.MultipartFile;





public interface StorageService {

    


    String upload(MultipartFile file, String folder);

    


    String buildPublicUrl(String storageKey);

    void delete(String storageKey);
}
