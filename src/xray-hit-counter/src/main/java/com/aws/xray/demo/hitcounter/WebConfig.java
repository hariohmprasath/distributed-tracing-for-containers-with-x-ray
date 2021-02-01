package com.aws.xray.demo.hitcounter;

import javax.servlet.Filter;
import javax.sql.DataSource;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.sql.TracingDataSource;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
public class WebConfig {

    private static final String SERVICE = "Hit-service";

    @Bean
    public Filter TracingFilter() {
        return new AWSXRayServletFilter(SERVICE);
    }

    static {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        AWSXRay.setGlobalRecorder(builder.build());
        AWSXRay.beginSegment(SERVICE);
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        DataSource dataSource = DataSourceBuilder.create()
            .driverClassName("com.mysql.cj.jdbc.Driver")
            .url("jdbc:mysql://" + System.getenv("RDS_HOSTNAME") + ":3306/hitdatabase?createDatabaseIfNotExist=true")
            .username(System.getenv("RDS_USERNAME"))
            .password(System.getenv("RDS_PASSWORD"))
            .build();

        return new TracingDataSource(dataSource);
    }
}
