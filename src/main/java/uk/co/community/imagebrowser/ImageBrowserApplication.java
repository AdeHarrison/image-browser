package uk.co.community.imagebrowser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ImageBrowserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageBrowserApplication.class, args);
    }
}
