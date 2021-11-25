package br.ucsal;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class AuxIot {

    private final Scanner scanner = new Scanner(System.in);
    private double lastRead = 0;
    private String serial;
    private ServerSocket socket;

    AuxIot(String serial) {
        this.serial = serial;
    }

    public static void main(String[] args) {
        String serial;
        if (args.length > 0) {
            serial = args[0];
        } else {
            serial = "3ew2";
        }

        AuxIot iot = new AuxIot(serial);
        iot.start();
    }

    public void start() {
        try {
            this.socket = new ServerSocket(64201, 1);
            System.out.println("MainIot executando em: " + this.getIpAddress() + ":" + this.getPort());
            work();
        } catch (Exception e) {
            System.out.println("Error start");
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
                        System.out.println("Aguardando conexão do mainIot");
                        Socket mainIotSocket = socket.accept();
                        //TODO: verificar ip do server
                        PrintWriter pw = new PrintWriter(mainIotSocket.getOutputStream(), true);
                        pw.print("LAST_READ " + lastRead);
                        pw.flush();
                        mainIotSocket.close();
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
                        System.out.println("Insira o novo valor do solo: ");
                        lastRead = Double.parseDouble(scanner.next());
                    }
                } catch (Exception e) {
                    System.out.println("Error serverThread");
                    e.printStackTrace();
                }
            }
        };
        updateThread.start();
    }

    public String getIpAddress() {
        return this.socket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        return this.socket.getLocalPort();
    }
}
