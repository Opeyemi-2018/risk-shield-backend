package RiskShield;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RiskShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskShieldApplication.class, args);
        System.out.print("hello world");
    }




}