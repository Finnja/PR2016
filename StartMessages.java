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
    private Entity ent;
    private String id;
    private int port_ecoute;

    // sera changé à faux si un message ne réussit pas à faire le tour de l'anneau
    public static boolean broken;
    public static String addr_multicast;
    public static int port_multicast;

    private ArrayList<String> deja_vus = new ArrayList<String>(); // messages que cette entité a déjà vu

    public StartMessages(Entity e) {
        this.ent = e;
        this.id = ent.id;
        this.port_ecoute = ent.port_ecoute;

        this.addr_multicast = ent.adr_diff;
        this.port_multicast = ent.port_diff;
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
                
                if (message.getBytes().length == 0) {
                    ;
                }
                // message est trop long
                else if (message.getBytes().length > 512) {
                    System.out.println("Le message est trop long (plus que 512 " + 
                            "octets). Il ne sera pas retransmis.");
                }
                // message est bon
                else {
                    // message divisé par les espaces
                    String[] mess_mots = message.split(" ");
                    String mess_type = mess_mots[0];

                    // format incorrect du message
                    if (!(Arrays.asList(mess_possibles).contains(mess_type))) {
                        System.out.println("Mauvais code de message - assurez-vous que le message " +
                                "est d'un format précisé dans l'énoncé du projet");
                    }
                    // code de message est bon
                    else {
                        //String mess_id = mess_mots[1]; // message id
                        String mess_id = Objects.toString(createID());

                        // message est de type "APPL"
                        if(mess_type.equals("APPL")) {
                            if(mess_mots.length < 2) {
                                System.out.println("Inclurez un message");
                            }
                            else {
                                String mess_content = message.substring(5);
                                message = "APPL " + mess_id + " DIFF#### " + 
                                        mess_content.length() + " " + mess_content;
                            }
                        }
                        // message est de type "WHOS"
                        else if(mess_type.equals("WHOS")) {
                            message = "WHOS " + mess_id;
                        }
                        // message est de type "GBYE"
                        else if(mess_type.equals("GBYE") && mess_mots.length == 1) {
                            String ip = InetAddress.getLocalHost().getHostAddress();
                            message = "GBYE " + mess_id + " " + ip + " " +
                                    this.port_ecoute + " " + this.ent.adr_suiv + " " +
                                    this.ent.port_suiv;
                        }
                        // si le message est un "TEST," il faut fixer un time-out
                        else if(mess_type.equals("TEST")) {
                            message = "TEST " + mess_id + " " + this.ent.adr_diff +
                                    " " + this.ent.port_diff;

                            addr_multicast = this.ent.adr_diff;
                            port_multicast = this.ent.port_diff;
                            Timer timer = new Timer();  
                            timer.schedule(new Timeout(), 8000); // 8 secondes
                        }

                        // voir si le message a déjà fait le tour de l'anneau
                        if (!(this.deja_vus).contains(mess_id)) {
                            (this.deja_vus).add(mess_id);

                            String mon_adr = InetAddress.getLocalHost().getHostAddress();
                            // envoie à son propre port UDP pour commencer le tour de l'anneau
                            InetSocketAddress ia = new 
                                    InetSocketAddress(mon_adr, this.port_ecoute);
                        
                            byte[] udp_data = message.getBytes();
                            DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                    udp_data.length, ia);

                            dso.send(paquet_send);
                        }
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

    public static void shutdown() {
        String addr_multicast = StartMessages.addr_multicast;
        int port_multicast = StartMessages.port_multicast;

        try {
            DatagramSocket md_sock = new DatagramSocket();

            String message = "DOWN";
            byte[] data = message.getBytes();

            InetSocketAddress ia = new 
                    InetSocketAddress(addr_multicast, port_multicast);
            DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
            
            System.out.println("En train d'envoyer le multicast to addr " + 
                    addr_multicast + " on port " + port_multicast + 
                    " : " + message);
            md_sock.send(paquet);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Timeout extends TimerTask {
    public void run() {
        if(Entity.broken) {
            StartMessages.broken = true;
            System.out.println("Anneau cassé.");
            StartMessages.shutdown();
        }
        else {
            System.out.println("L'anneau fonctionne toujours.");
        }
    }
}
