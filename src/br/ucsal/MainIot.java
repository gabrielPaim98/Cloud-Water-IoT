package br.ucsal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class MainIot {
    private static final int MIN_15 = 900000;
    private static final int MIN_3 = 180000;
    private static final int MIN_1 = 60000;
    private static final int SEC_30 = 30000;

    private final Scanner scanner = new Scanner(System.in);
    private boolean isFaucetOn = false;
    private String serial;
    private ServerSocket socket;
    private List<AuxDevice> deviceList = new ArrayList<AuxDevice>();

    private HttpServer server;
    ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    MainIot(String serial) {
        this.serial = serial;
    }

    public static void main(String[] args) {
        String serial;
        if (args.length > 0) {
            serial = args[0];
        } else {
            serial = "1qw2";
        }

        MainIot iot = new MainIot(serial);
        iot.start();
    }

    public void start() {
        try {
            this.socket = new ServerSocket(64200, 1);
            createServer();

            String iotUrl = this.getIotIpAddress() + ":" + this.getIotPort();
            String serverUrl = this.getServerIpAddress() + ":" + this.getServerPort();

            System.out.println("MainIot executando em: " + iotUrl);
            System.out.println("MainIotServer executando em: " + serverUrl);

            menu();
        } catch (Exception e) {
            System.out.println("Error start");
            e.printStackTrace();
        }
    }

    public void createServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 64201), 0);
            server.createContext("/faucet", new MyHttpHandler());
            server.setExecutor(threadPoolExecutor);
            server.start();

        } catch (Exception e) {
            System.out.println("Error creating server");
            e.printStackTrace();
        }

    }

    public void menu() {
        try {
            while (true) {
                System.out.println("1- Adicionar novo dispositivo auxiliar");
                System.out.println("2- Atualizar link ngrok");
                System.out.println("3- Sair");
                int selectedOption = scanner.nextInt();
                if (selectedOption == 1) {
                    addAuxDevice();
                } else if (selectedOption == 2) {
                    updateServerLink();
                } else if (selectedOption == 3) {
                    work();
                    break;
                } else {
                    System.out.println("Opção inválida");
                }
            }
        } catch (Exception e) {
            System.out.println("Error menu");
            e.printStackTrace();
        }
    }

    public void updateServerLink() {
        /*
            {
                main_iot_serial: "7bd4",
                link: "http://127.0.0.1:8080"
            }
            */
        System.out.println("Informe o link do ngrok");
        String ngrok = scanner.next();

        JSONObject json = new JSONObject();
        json.put("main_iot_serial", this.serial);
        json.put("link", ngrok);
        sendPost(json, "updateIotLink");
    }

    public void work() {
        Thread updateThread = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        updateLastSoilRead();
                        updateServer();
                        Thread.sleep(MIN_3);
                    }
                } catch (Exception e) {
                    System.out.println("Error serverThread");
                    e.printStackTrace();
                }
            }
        };
        updateThread.start();
    }

    public void updateLastSoilRead() {
        try {
            System.out.println("Atualizando leitura do solo");
            deviceList.forEach((auxDevice -> {
                try {
                    System.out.println("Conectando ao aux " + auxDevice.getSerial());
                    auxDevice.setSocket(new Socket(auxDevice.getIp(), auxDevice.getPort()));
                    String message;
                    BufferedReader in = new BufferedReader(new InputStreamReader(auxDevice.getSocket().getInputStream()));
                    while ((message = in.readLine()) != null) {
                        if (message.contains("LAST_READ")) {
                            double lastRead = Double.parseDouble(message.split(" ")[1]);
                            auxDevice.setLastSoilRead(lastRead);
                            auxDevice.getSocket().close();
                            auxDevice.setSocket(null);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error connecting to device" + auxDevice.getSerial());
                    e.printStackTrace();
                }
            }));
        } catch (Exception e) {
            System.out.println("Error updateLastSoilRead");
            e.printStackTrace();
        }
    }

    public void updateServer() {
        try {
            JSONObject json = new JSONObject();
            json.put("main_iot_serial", serial);
            JSONArray jArray = new JSONArray();
            deviceList.forEach((auxDevice -> {
                JSONObject jObject = new JSONObject();
                jObject.put("aux_iot_serial", auxDevice.getSerial());
                jObject.put("read_value", auxDevice.getLastSoilRead());
                jArray.add(jObject);
            }));

            json.put("log", jArray);

            sendPost(json, "addLog");

        /*{
            "main_iot_serial": "1qw2",
            "log": [
                {
                    "aux_iot_serial": "3ew2",
                    "read_value": "0.0022"
                }
            ]
        }*/
        } catch (Exception e) {
            System.out.println("Error updateServer");
            e.printStackTrace();
        }
    }

    public void addAuxDevice() {
        try {
            System.out.println("Informe o serial do dispositivo:");
            String deviceSerial = scanner.next();
            System.out.println("Informe o ip e porta do dispositivo:");
            String msg = scanner.next();
            String ip = msg.split(":")[0];
            int port = Integer.parseInt(msg.split(":")[1]);
            AuxDevice auxDevice = new AuxDevice(deviceSerial, ip, port);
            deviceList.add(auxDevice);
        } catch (Exception e) {
            System.out.println("Error addAuxDevice");
            e.printStackTrace();
        }
    }

    public void sendPost(JSONObject json, String route) {
        System.out.println("Sending post to " + route);
        System.out.println("body " + json.toJSONString());
        OkHttpClient httpClient = new OkHttpClient();
        MediaType JSON
                = MediaType.parse("application/json; charset=utf-8");
        RequestBody formBody = FormBody.create(JSON, json.toJSONString());

        Request request = new Request.Builder()
//                .url("http://localhost:5001/cloud-water-ac2cb/us-central1/" + route) //TODO: url local
                .url("https://us-central1-cloud-water-ac2cb.cloudfunctions.net/" + route) //TODO: url prd
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Get response body
            System.out.println("resposta do server: " + response.body().string());
        } catch (Exception e) {
            System.out.println("Error sendPost");
        }
    }

    public String getIotIpAddress() {
        return this.socket.getInetAddress().getHostAddress();
    }

    public int getIotPort() {
        return this.socket.getLocalPort();
    }

    public String getServerIpAddress() {
        return this.server.getAddress().getAddress().getHostAddress();
    }

    public int getServerPort() {
        return this.server.getAddress().getPort();
    }

    private class MyHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String requestParamValue = null;
            if ("GET".equals(httpExchange.getRequestMethod())) {
                requestParamValue = handleGetRequest(httpExchange);
            } else if ("POST".equals(httpExchange.getRequestMethod())) {
                requestParamValue = handlePostRequest(httpExchange);
            }
            handleResponse(httpExchange, requestParamValue);
        }

        private String handleGetRequest(HttpExchange httpExchange) {
            /*return httpExchange.
                    getRequestURI()
                    .toString()
                    .split("\\?")[1]
                    .split("=")[1];*/
            return null;
        }

        private String handlePostRequest(HttpExchange httpExchange) {
            String data = null;
            try {
                InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);

                int b;
                StringBuilder buf = new StringBuilder(512);
                while ((b = br.read()) != -1) {
                    buf.append((char) b);
                }

                data = buf.toString();
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(data);
                String jsonStatus = (String) json.get("status");
                if (jsonStatus == null) {
                    System.out.println("No status found for json " + data);
                    return null;
                }
                String oldStatus;
                String newStatus;
                if (isFaucetOn) {
                    oldStatus = "Ligada";
                } else {
                    oldStatus = "Desligada";
                }
                if (jsonStatus.equalsIgnoreCase("on")) {
                    isFaucetOn = true;
                    newStatus = "Ligada";
                } else {
                    isFaucetOn = false;
                    newStatus = "Desligada";
                }
                System.out.println("Novo comando do servidor " + oldStatus + " -> " + newStatus);

                br.close();
                isr.close();
            } catch (Exception e) {
                System.out.println("Error handling post");
                e.printStackTrace();
            }
            return data;
        }

        private void handleResponse(HttpExchange httpExchange, String requestParamValue) throws IOException {
            OutputStream outputStream = httpExchange.getResponseBody();
            String response;
            int responseCode;

            if (requestParamValue == null) {
                responseCode = 400;
                response = "Failed to update faucet status";
            } else {
                responseCode = 200;
                response = "Faucet status updated";
            }

//            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(responseCode, response.length());

            outputStream.write(response.getBytes());
            outputStream.flush();
            outputStream.close();
        }
    }
}
