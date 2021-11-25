package br.ucsal;

import java.net.Socket;

public class AuxDevice {
    private String serial;
    private Socket socket;
    private String ip;
    private int port;
    private double lastSoilRead = 0;

    AuxDevice(String serial, String ip, int port) {
        this.serial = serial;
        this.ip = ip;
        this.port = port;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public double getLastSoilRead() {
        return lastSoilRead;
    }

    public void setLastSoilRead(double lastSoilRead) {
        this.lastSoilRead = lastSoilRead;
    }
}
