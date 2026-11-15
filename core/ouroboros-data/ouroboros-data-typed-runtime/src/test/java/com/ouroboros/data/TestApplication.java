package com.ouroboros.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.ouroboros.data.annotation.DataModelScan;

@DataModelScan
@SpringBootApplication
public class TestApplication {

  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }
}
