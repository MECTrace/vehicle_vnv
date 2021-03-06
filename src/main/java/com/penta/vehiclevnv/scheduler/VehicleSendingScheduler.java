package com.penta.vehiclevnv.scheduler;

import com.penta.vehiclevnv.configuration.FileInfoProperties;
import com.penta.vehiclevnv.constant.VehicleCertMap;
import com.penta.vehiclevnv.domain.VehicleCert;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class VehicleSendingScheduler implements ApplicationListener<ContextClosedEvent> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private VehicleCertMap vehicleCertMap;

    private final LocalDateTime start = LocalDateTime.now();
    private final Path targetLocation;
    private final Path doneLocation;
    private final Environment environment;

    public VehicleSendingScheduler(FileInfoProperties properties, VehicleCertMap vehicleCertMap, Environment environment) {
        this.targetLocation = Paths.get(properties.getTarget()).toAbsolutePath().normalize();
        this.doneLocation = Paths.get(properties.getDone()).toAbsolutePath().normalize();
        this.vehicleCertMap = vehicleCertMap;
        this.environment = environment;
    }

    @PostConstruct
    @SneakyThrows
    public void init() {
        Files.createDirectories(targetLocation);
        Files.createDirectories(doneLocation);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {

        int success = SendingThread.count.getSuccess().get();
        int fail = SendingThread.count.getFail().get();
        int others = SendingThread.count.getOthers().get();
        logger.info("-----------------------------------------");
        logger.info("                 summary                 ");
        logger.info("-----------------------------------------");
        logger.info("SUCCESS  :: {}",success);
        logger.info("FAIL     :: {}",fail);
        logger.info("OTHERS   :: {}",others);
        logger.info("TOTAL    :: {}",success + fail + others);
        logger.info("-----------------------------------------");

        logger.info("Application ?????? ?????? :: {}",this.start);
        logger.info("Application ?????? ?????? :: {}",LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimestamp()), TimeZone.getDefault().toZoneId()));

    }

    @Scheduled(fixedDelay = 60000)
    @SneakyThrows
    public void sendToEdge() {

        logger.info("???????????? ??????");

        if (checkFilePresent(this.targetLocation)) {

            File dir = new File(this.targetLocation.toString());
            File[] fileList = dir.listFiles();

            /*
            * ???????????? ??????????????? ???????????? & VehicleCertMap??? ????????? ??????????????? ???????????? ??????.
            * ?????? ?????? ????????? ?????? ?????? ?????? X
            * */
            Map<String, List<File>> fileMap = Arrays.stream(fileList)
                    .filter(file -> verifyFile(file.getName().replaceAll(" ","")))
                    .collect(Collectors.groupingBy(file -> getCarNo(file.getName())));

            Object[] keyArray = fileMap.keySet().toArray();
            int limited = 0;
            if(keyArray.length > 10) {
                limited = keyArray.length/2;  // ????????????(???or???)??? ????????? ????????? ??????
            } else {
                limited = keyArray.length;    // ??????????????? 10??? ????????? ?????? ?????????????????? ????????? ??????
            }

            Thread[] t = new Thread[limited];

            for(int i = 0; i < limited; i++) {
                String key = keyArray[i].toString();
                List<File> values = fileMap.get(key);
                VehicleCert vehicleCert = vehicleCertMap.getVehicleCertInfo(key);
                Runnable r = new SendingThread(key, values.get(0), vehicleCert, this.targetLocation, this.doneLocation);
                t[i] = new Thread(r);
                t[i].start();
            }

        } else {
            logger.info("------- {} ??? ????????? ???????????? ?????? -------",this.targetLocation.toString());
        }
    }

    @SneakyThrows
    private boolean verifyFile(String fileName) {
        String carNo = getCarNo(fileName);
        boolean result = StringUtils.hasText(carNo) ? vehicleCertMap.hasVehicleNo(carNo) : false;
        return result;
    }

    private String getCarNo(String fileName) {
        String trimStr = fileName.trim().replaceAll(" ","");
        Pattern pattern = Pattern.compile("\\d{2,3}[???-???]{1}\\d{4}");
        Matcher matcher = pattern.matcher(trimStr);
        return matcher.find() ? matcher.group() : "";
    }

    @SneakyThrows
    private boolean checkFilePresent(Path path) {
        try (Stream<Path> entries = Files.list(path)) {
            return entries.filter(f -> !(f.toString().contains(".DS_Store")) && !(f.toFile().isDirectory())).findFirst().isPresent();
        }
    }



}
