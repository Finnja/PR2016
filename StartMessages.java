/* Recevoir les messages du serveur et les imprimer
 * à stdout
*/

import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Objects;
import java.lang.Math;

public class StartMessages implements Runnable {
    private long id;
    private int port_ecoute;

    private ArrayList<String> deja_vus = new ArrayList<String>(); // messages que cette entité a déjà vu

    public StartMessages(long id, int pe) {
        this.id = id;
        this.port_ecoute = pe;
    }

    /* Méthode pour attendre l'entrée d'un message au stdin par l'utilisateur,
       puis l'envoyer autour de l'anneau
    */
    public void run() {
        try {
            String[] mess_possibles = {"WELC", "NEWC", "ACKC", "APPL", "WHOS",
                    "MEMB", "GBYE", "EYBG", "TEST", "DOWN", "DUPL", "ACKD"};
    
            Scanner scan = new Scanner(System.in);
            // ouvre une socket juste pour envoyer - pas nécessaire de spécifier le port
            DatagramSocket dso = new DatagramSocket();

            while (true) {
                String message = scan.nextLine();
                // message divisé par les espaces
                String[] mess_mots = message.split(" ");
                String mess_type = mess_mots[0];
                String mess_content = null;

                // format incorrect du message
                if (!(Arrays.asList(mess_possibles).contains(mess_type))) {
                    System.out.println("Mauvais code de message - assurez-vous que le message " +
                            "est d'un format précisé dans l'énoncé du projet");
                }
                // code de message est bon
                else {
                    //String mess_id = mess_mots[1]; // message id
                    String mess_id = Objects.toString(createID());

                    // message is a "APPL"
                    if(mess_type.equals("APPL")) {
                        if(mess_mots.length < 2) {
                            System.out.println("Inclurez un message");
                        }
                        else {
                            mess_content = message.substring(5);
                        }
                    }
                    
                    // si le message est un "TEST," il faut fixer un time-out
                    else if(mess_type.equals("TEST")) {
                        Timer timer = new Timer();  
                        timer.schedule(new Timeout(), 2000); // 2 secondes
                    }

                    // voir si le message a déjà fait le tour de l'anneau
                    if (!(this.deja_vus).contains(mess_id)) {
                        (this.deja_vus).add(mess_id);

                        String mon_adr = InetAddress.getLocalHost().getHostAddress();
                        // envoie à son propre port UDP pour commencer le tour de l'anneau
                        InetSocketAddress ia = new 
                                InetSocketAddress(mon_adr, this.port_ecoute);
                    
                        String message_send = "APPL " + mess_id + " DIFF#### " + 
                                mess_content.length() + " " + mess_content;
                        byte[] udp_data = message_send.getBytes();
                        DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                udp_data.length, ia);

                        dso.send(paquet_send);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // crée des IDs uniques de 8 octets
    private static long createID() {
        UUID u = UUID.randomUUID();
        long ul = u.getLeastSignificantBits(); // 8 octets
        long ul_abs = Math.abs(ul); // valeur absolue

        return ul_abs;
    }
}

class Timeout extends TimerTask {
    public void run() {
        System.out.println("Time's up!");
        System.exit(0);
    }
}
