package com.example.demoapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dc3.api.center.manager.feign.DriverInfoClient;
import com.dc3.common.bean.R;
import com.dc3.common.dto.DriverInfoDto;
import com.dc3.common.model.DriverInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class DemoAppApplicationTests {

//    @Resource
//    private DriverInfoClient driverInfoClient;

    @Test
    void contextLoads() {
//        R<Page<DriverInfo>> list = driverInfoClient.list(null);
//        list.getData().getRecords().forEach(System.out::println);
    }

}
