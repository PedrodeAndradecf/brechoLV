package Backend.BrechoAPI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


















@SpringBootApplication
@ConfigurationPropertiesScan
public class BrechoApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrechoApiApplication.class, args);
	}
}
