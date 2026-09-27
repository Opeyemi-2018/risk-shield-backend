package RiskShield;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RiskShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskShieldApplication.class, args);
        System.out.print("hello world");
    }


}