package com.example.demoapp.init;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dc3.api.center.data.feign.PointValueClient;
import com.dc3.api.center.manager.feign.*;
import com.dc3.common.bean.R;
import com.dc3.common.bean.driver.PointValue;
import com.dc3.common.constant.Common;
import com.dc3.common.dto.PointAttributeDto;
import com.dc3.common.model.*;

import com.dc3.common.valid.Read;
import com.dc3.common.valid.ValidatableList;
import com.dc3.common.valid.Write;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

@Component
@EnableFeignClients(basePackages = {
        "com.dc3.api.center.manager.*",
        "com.dc3.api.center.data.*"
})
@ComponentScan(basePackages = {
        "com.dc3.api.center.date",
        "com.dc3.api.center.manager"
})

@Slf4j
public class DemoInit implements ApplicationRunner {


    @Resource
    DriverClient driverClient;

    @Resource
    ProfileClient profileClient;

    @Resource
    DriverInfoClient driverInfoClient;

    @Resource
    PointClient pointClient;

    @Resource
    PointAttributeClient pointAttributeClient;

    @Resource
    GroupClient groupClient;

    @Resource
    DriverAttributeClient  driverAttributeClient;

    @Resource
    DeviceClient deviceClient;

    @Resource
    PointInfoClient pointInfoClient;

    @Resource
    PointValueClient pointValueClient;

    static String pubTopic = "mqtt/group/device/";
    static String subTopic = "cmd/";
    static int qos = 0;
    static String broker = "tcp://dc3-rabbitmq:1883";
    private static final String NAME = "java-demo-app-24" ;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        //获取mqtt驱动及其属性
        R<Driver> mqttR = driverClient.selectByServiceName("DC3-DRIVER-MQTT");

        if (!R.ok().isOk()) {
            log.error("Driver no found");
            System.exit(1);
        }
        Driver mqtt = mqttR.getData();

        //新增模板(profile)
        R<Profile> profileR = profileClient.add(new Profile().setName(NAME).setDriverId(mqtt.getId()).setShare(false));
        assert profileR.isOk();
        Profile profile = profileR.getData();

        R<Page<PointAttribute>> pageR = pointAttributeClient.list(new PointAttributeDto());
        assert pageR.isOk();
        PointAttribute pointAttributeQOS = null;
        PointAttribute pointAttributeTopic = null;
        for (PointAttribute pointAttribute : pageR.getData().getRecords()) {
            switch (pointAttribute.getName()) {
                case "commandQos":
                    pointAttributeQOS = pointAttribute;
                    break;
                case "commandTopic":
                    pointAttributeTopic = pointAttribute;
                    break;
            }
        }
        assert pointAttributeQOS != null;
        assert pointAttributeTopic != null;
        //新增位点(point), 绑定模板
        R<Point> pointRQOS = pointClient.add(new Point().setName(NAME + "QOS").setFormat("%.3f").setMaximum(999999f)
                .setMinimum(-999999f).setType("int").setProfileId(profile.getId()).setRw((short)0)
                .setBase(0f).setUnit("").setAccrue(false));
        R<Point> topicRQOS = pointClient.add(new Point().setName(NAME + "TOPIC").setFormat("%.3f").setMaximum(999999f)
                .setMinimum(-999999f).setType("string").setProfileId(profile.getId()).setRw((short)0)
                .setBase(0f).setUnit("").setAccrue(false));

        Point pointQOS = pointRQOS.getData();
        Point topicQOS = topicRQOS.getData();

        //新增组(group)
        R<Group> groupR = groupClient.add(new Group().setName(NAME + "_grp"));
        Group group = groupR.getData();

        //新增设备
        DemoMQTT demoMQTT = new DemoMQTT();
        synchronized (this) {
            R<Device> deviceR = deviceClient.add(new Device().setName(NAME + "_device").setProfileId(profile.getId())
                    .setGroupId(group.getId()));
            assert deviceR.isOk();
            demoMQTT.setDevice(deviceR.getData());
            demoMQTT.setMqttClient(newMqttClient());
            //绑定位点与设备
            pointInfoClient.add(new PointInfo().setPointId(pointQOS.getId()).setValue("0").
                    setDeviceId(demoMQTT.getDevice().getId()).setPointAttributeId(pointAttributeQOS.getId()));
        }

       new Thread( ()->{
            try {
                DevicePayLoad devicePayLoad = new DevicePayLoad().setDeviceId(demoMQTT.getDevice().getId())
                        .setPointId(pointQOS.getId()).setValue(String.valueOf(System.currentTimeMillis() & 0xffffff));
                String tpc = pubTopic + demoMQTT.getDevice().getId();
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                String json = ow.writeValueAsString(devicePayLoad);
                demoMQTT.getMqttClient().publish(tpc,
                        json.getBytes(),0, false );

                log.info(tpc);
                log.info(json);

               Thread.sleep(500);
           } catch (Exception e) {
               e.printStackTrace();
           }
       }).start();


        Thread.sleep(500);
        do {
            R<PointValue> latest = pointValueClient.latest(demoMQTT.getDevice().getId());
            assert latest.isOk();
            log.info("driver received message : " + latest.getData());
            if(Double.parseDouble(latest.getData().getValue()) < 0xffff){
                ValidatableList<CmdParameter> cmdParameters = new ValidatableList<>();
                cmdParameters.add(new CmdParameter().setPointId(topicQOS.getId()).
                        setDeviceId(demoMQTT.getDevice().getId()).setValue("shutdown"));
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                String json = ow.writeValueAsString(cmdParameters);
                URL url = new URL(Common.Service.DC3_DRIVER_URL_PREFIX + "/write");
                URLConnection con = url.openConnection();
                HttpURLConnection http = (HttpURLConnection)con;
                http.setRequestMethod("POST"); // PUT is another valid option
                http.setDoOutput(true);
                http.setFixedLengthStreamingMode(json.length());
                http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                http.connect();
                try(OutputStream os = http.getOutputStream()) {
                    os.write(json.getBytes());
                }
//                driverCommandApi.writePoint(cmdParameters);
                break;
            }
        }while (true);

    }

    private static  MqttClient newMqttClient() throws MqttException {

        String clientId = (NAME + System.currentTimeMillis()).substring(0, 16);
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setUserName("dc3");
        connOpts.setPassword("dc3".toCharArray());
        // 保留会话
        connOpts.setCleanSession(true);
        MqttClient mqttClient = new MqttClient(broker,clientId);
        mqttClient.connect(connOpts);
        mqttClient.subscribe(subTopic);
        mqttClient.subscribe(subTopic, (s, mqttMessage) -> {
            log.info("MQTT_DEVICE received  message : " + s);
            if ("shutdown".equalsIgnoreCase(s)){
                log.info("shutdown now");
                System.exit(0);
            }
        });
        return mqttClient;
    }

}

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
class DemoMQTT{
    private  MqttClient mqttClient;
    private Device device;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
class DevicePayLoad {
    private Long deviceId;
    private Long pointId;
    private String value;
}

class MQTT{
    public static void main(String[] args) {
        String subTopic = "testtopic/#";
        String pubTopic = "testtopic/1";
        String content = "Hello World";
        int qos = 2;
        String broker = "tcp://broker.emqx.io:1883";
        String clientId = "emqx_test";
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            MqttClient client = new MqttClient(broker, clientId, persistence);

            // MQTT 连接选项
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName("emqx_test");
            connOpts.setPassword("emqx_test_password".toCharArray());
            // 保留会话
            connOpts.setCleanSession(true);

            // 设置回调
//            client.setCallback(new PushCallback());

            // 建立连接
            System.out.println("Connecting to broker: " + broker);
            client.connect(connOpts);

            System.out.println("Connected");
            System.out.println("Publishing message: " + content);

            // 订阅
            client.subscribe(subTopic);

            // 消息发布所需参数
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(qos);
            client.publish(pubTopic, message);
            System.out.println("Message published");

            client.disconnect();
            System.out.println("Disconnected");
            client.close();
            System.exit(0);
        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }
    }

}
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
class CmdParameter {
    @NotNull(message = "device id can't be empty", groups = {Read.class, Write.class})
    private Long deviceId;

    @NotNull(message = "point id can't be empty", groups = {Read.class, Write.class})
    private Long pointId;

    @NotBlank(message = "value can't be empty", groups = {Write.class})
    private String value;
}
