package br.ucsal;

import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MainIot {
    private static final int MIN_15 = 900000;
    private static final int MIN_3 = 180000;
    private static final int SEC_30 = 30000;

    private final Scanner scanner = new Scanner(System.in);
    private boolean isFaucetOn = false;
    private String serial;
    private ServerSocket socket;
    private List<AuxDevice> deviceList = new ArrayList<AuxDevice>();

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
            String url = this.getIpAddress() + ":" + this.getPort();
            System.out.println("MainIot executando em: " + url);

            /*
            {
                main_iot_serial: "7bd4",
                link: "http://127.0.0.1:8080"
            }
            */
            JSONObject json = new JSONObject();
            json.put("main_iot_serial", this.serial);
            json.put("link", url);
            sendPost(json, "updateIotLink");
            menu();
        } catch (Exception e) {
            System.out.println("Error start");
            e.printStackTrace();
        }
    }

    public void menu() {
        try {
            while (true) {
                System.out.println("1- Adicionar novo dispositivo auxiliar");
                System.out.println("2- Sair");
                int selectedOption = scanner.nextInt();
                if (selectedOption == 1) {
                    addAuxDevice();
                } else if (selectedOption == 2) {
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

    public void work() {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        String faucetStatus;
                        if (isFaucetOn) {
                            faucetStatus = "Ligada";
                        } else {
                            faucetStatus = "Desligada";
                        }
                        System.out.println("Aguardando conexão do servidor (torneira " + faucetStatus + ")");
                        try {
                            Socket serverSocket = socket.accept();
                            System.out.println("Conectado ao server: " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getPort());
                            if (serverSocket.getInetAddress().getHostAddress().equals("127.0.0.1")) { //TODO: modificar para ip do server
                                serverSocket.close();
                                continue;
                            }
                            String message = null;
                            BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                            while ((message = in.readLine()) != null) {
                                System.out.println("Nova mensagem: " + message);
                                if (message.contains("FAUCET_STATUS ON")) {
                                    isFaucetOn = true;
                                } else if (message.contains("FAUCET_STATUS OFF")) {
                                    isFaucetOn = false;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Conexão com servidor perdida");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error serverThread");
                    e.printStackTrace();
                }
            }
        };
        serverThread.start();

        Thread updateThread = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    while (true) {
                        updateLastSoilRead();
                        updateServer();
//                        Thread.sleep(SEC_30);
                        Thread.sleep(MIN_15);
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
                .url("http://localhost:5001/cloud-water-ac2cb/us-central1/" + route) //TODO: mudar url
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Get response body
            System.out.println("resposta do server" + response.body().string());
        } catch (Exception e) {
            System.out.println("Error sendPost");
        }
    }

    public String getIpAddress() {
        return this.socket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        return this.socket.getLocalPort();
    }
}
