package dev.felippevaz;

import dev.felippevaz.server.RequestServer;

public class Main {

    public static void main(String[] args) {



        RequestServer server = new RequestServer(8080);
        server.registerController(new controller());

        server.start();
    }
}
