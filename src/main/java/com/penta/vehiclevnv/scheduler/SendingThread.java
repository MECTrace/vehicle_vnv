package com.penta.vehiclevnv.scheduler;

import com.penta.vehiclevnv.constant.EdgeNode;
import com.penta.vehiclevnv.domain.VehicleCert;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Random;

@Slf4j
public class SendingThread implements Runnable {

    private String carNo;
    private File file;
    private VehicleCert vehicleCert;
    private Path targetLocation;
    private Path doneLocation;


    public SendingThread(String carNo, File file, VehicleCert vehicleCert, Path targetLocation, Path doneLocation) {
        this.carNo = carNo;
        this.file = file;
        this.vehicleCert = vehicleCert;
        this.targetLocation = targetLocation;
        this.doneLocation = doneLocation;
    }


    @Override
    @SneakyThrows
    public void run() {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // 1개의 파일만 보내는 것으로 수정
        body.add("file", new FileSystemResource(file));
        body.add("signature", getSignatureResource(file, vehicleCert));

        long beforeTime = System.currentTimeMillis();
        log.info("데이터 전송 시작 current time ms :: {}", beforeTime);
        ResponseEntity<String> response = sendRequest(body, vehicleCert);
        long afterTime = System.currentTimeMillis();
        long secDiffTime = (afterTime - beforeTime)/1000;
        log.info("데이터 전송 완료 current time ms :: {}", afterTime);
        log.info("소요시간 :: {}", secDiffTime);

        isSuccess(response);

    }

    @SneakyThrows
    private boolean isSuccess(ResponseEntity<String> response) {
        // 5xx 응답 : edge server error
        if (String.valueOf(response.getStatusCodeValue()).startsWith("5")) {
            log.error("송신결과 : [5xx FAIL] Response :: " + response);
            return false;
        } else if (String.valueOf(response.getStatusCodeValue()).startsWith("4")) {
            // 4xx 응답 : 요청 에러
            log.error("송신결과 : [4xx FAIL] Response :: " + response);
            return false;
        } else {
            log.info("송신결과 : [SUCCESS] :: " + response);
            return true;
        }
    }

    @SneakyThrows
    private ByteArrayResource getSignatureResource(File file, VehicleCert vehicleCert) {
        // 서명
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(getPrivateKey(vehicleCert));

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        signature.update(fileBytes);

        byte[] digitalSignature = signature.sign();

        // * Getter 필요
        ByteArrayResource resource = new ByteArrayResource(digitalSignature) {
            @Override
            public String getFilename() {
                return "signature";
            }
        };

        return resource;
    }

    private ResponseEntity<String> sendRequest(MultiValueMap<String, Object> body, VehicleCert vehicleCert) {
        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, header);

        // edge random 전송
        EdgeNode edge = EdgeNode.values()[new Random().nextInt(4)];

        // TODO :: 로컬테스트용 아래 주석 풀고 사용
/*
        return getRestTemplate(vehicleCert)
                .postForEntity("https://127.0.0.1:8443/api/edge/upload/vehicle/", requestEntity, String.class);
 */

        return getRestTemplate(vehicleCert)
                .postForEntity("https://" + edge.getIP() + ":8443/api/edge/upload/vehicle/", requestEntity, String.class);

    }


    @SneakyThrows
    private RestTemplate getRestTemplate(VehicleCert cert) {

        log.info("------------------------차량 인증서 정보 start------------------------");
        log.info("CERTPATH :: {} ", cert.getCertPath());
        log.info("CERT-ALIAS :: {} ", cert.getCertAlias());
        log.info("TRUST_STORE_PATH :: {} ", cert.getTrustStorePath());
        log.info("AS URI >>>>> {}", Paths.get(cert.getCertPath()).toUri().toURL());
        log.info("------------------------차량 인증서 정보 end------------------------");

        SSLContext sslContext = new SSLContextBuilder()
                .loadKeyMaterial(Paths.get(cert.getCertPath()).toUri().toURL(), cert.getCertPassword().toCharArray(), cert.getCertPassword().toCharArray())
                .loadTrustMaterial(Paths.get(cert.getTrustStorePath()).toUri().toURL(), cert.getTrustStorePassword().toCharArray())
                .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        HttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(socketFactory)
                .build();
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }

    @SneakyThrows
    private PrivateKey getPrivateKey(VehicleCert cert) {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(cert.getCertPath()), cert.getCertPassword().toCharArray());
        // keyStore.load(new FileInputStream("/Users/penta/IdeaProjects/cloudEdge/transmitter/src/main/resources/client-key.jks"), this.keyPassword.toCharArray());
       // return (PrivateKey) keyStore.getKey(cert.getCertAlias(), cert.getCertPassword().toCharArray());
       return (PrivateKey) keyStore.getKey(cert.getCertAlias(), cert.getCertPassword().toCharArray());
    }


}
