package com.dc3.demodriver.service.impl;

import com.dc3.common.model.Device;
import com.dc3.common.model.Point;
import com.dc3.common.sdk.bean.AttributeInfo;
import com.dc3.common.sdk.service.CustomDriverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author resyon
 * @date 2021/1/25 17:27
 */
@Service
@Slf4j
public class DemoDriverServiceImpl implements CustomDriverService {
    static AtomicLong val;
    /**
     * Initial Driver
     */
    @Override
    public void initial() {
        val = new AtomicLong(0L);
        log.info("初始化-----");
    }

    /**
     * Read Operation
     *
     * @param driverInfo Driver Attribute Info
     * @param pointInfo  Point Attribute Info
     * @param device     Device
     * @param point      Point
     * @return String Value
     * @throws Exception Exception
     */
    @Override
    public String read(Map<String, AttributeInfo> driverInfo, Map<String, AttributeInfo> pointInfo, Device device, Point point) throws Exception {
        long rtn = val.getAndIncrement();
        log.info("read from device={} and get val={}", device.getId(), val);
        return String.valueOf(rtn);
    }

    /**
     * Write Operation
     *
     * @param driverInfo Driver Attribute Info
     * @param pointInfo  Point Attribute Info
     * @param device     Device
     * @param value      Value Attribute Info
     * @return Boolean Boolean
     * @throws Exception Exception
     */
    @Override
    public Boolean write(Map<String, AttributeInfo> driverInfo, Map<String, AttributeInfo> pointInfo, Device device, AttributeInfo value) throws Exception {
        return null;
    }

    /**
     * 驱动本身存在定时器，用于定时采集数据和下发数据，该方法为用户自定义操作，系统根据配置定时执行
     */
    @Override
    public void schedule() {

    }
}
