package com.penta.vehiclevnv.scheduler;

import com.penta.vehiclevnv.configuration.FileInfoProperties;
import com.penta.vehiclevnv.constant.VehicleCertMap;
import com.penta.vehiclevnv.domain.VehicleCert;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
@Slf4j
public class VehicleSendingScheduler implements ApplicationListener<ContextClosedEvent> {


    private VehicleCertMap vehicleCertMap;

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
        log.info("-----------------------------------------");
        log.info("                 summary                 ");
        log.info("-----------------------------------------");
        log.info("SUCCESS :: {}",success);
        log.info("FAIL :: {}",fail);
        log.info("OTHERS :: {}",others);
        log.info("TOTAL :: {}",success + fail + others);
        log.info("-----------------------------------------");

        log.info("Application 종료");
        log.info("종료시간 :: {}",event.getTimestamp());

    }

    @Scheduled(fixedDelay = 60000)
    @SneakyThrows
    public void sendToEdge() {

        log.info("프로세스 시작");

        if (checkFilePresent(this.targetLocation)) {

            File dir = new File(this.targetLocation.toString());
            File[] fileList = dir.listFiles();

            /*
            * 파일명에 차량번호가 존재하고 & VehicleCertMap에 등록된 차량번호인 경우에만 전송.
            * 아닌 경우 별도의 로직 없이 전송 X
            * */
            Map<String, List<File>> fileMap = Arrays.stream(fileList)
                    .filter(file -> verifyFile(file.getName().replaceAll(" ","")))
                    .collect(Collectors.groupingBy(file -> getCarNo(file.getName())));

            Object[] keyArray = fileMap.keySet().toArray();
            int limited = 0;
            if(keyArray.length > 10) {
                limited = keyArray.length/2;  // 전체파일(홀or짝)의 절반만 쓰레드 생성
            } else {
                limited = keyArray.length;    // 차량갯수가 10개 이하일 경우 차량갯수만큼 쓰레드 생성
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
            log.info("------- {} 에 파일이 존재하지 않음 -------",this.targetLocation.toString());
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
        Pattern pattern = Pattern.compile("\\d{2,3}[가-힣]{1}\\d{4}");
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
