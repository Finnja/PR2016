/* Program to insert new entity into ring network
   by connecting via TCP to entity already in ring
*/

import java.net.*;
import java.io.*;
import java.util.UUID;
import java.lang.Math;

public class InsertEntity {

	public static void main(String[] args) {
		try {
            // vérifie arguments
            if (args.length != 4) {
                System.out.println("Usage : java InsertEntity <mon_port_udp> <mon_port_tcp> " +
                        "<adresse_pour_se_connecter> <port_tcp_pour_se_connecter> " + 
                        "(<adresse_pour_se_connecter> should be 127.0.0.1 for now)"); 
            }
            else {
                // port/adr ce CETTE (nouvelle) entité
                String mon_adr = InetAddress.getLocalHost().getHostAddress();
                int mon_port_udp = Integer.parseInt(args[0]);
                int port_tcp = Integer.parseInt(args[1]);

                // port/adr de l'entité à laquelle on souhaite se connecter
                String adr_conn = args[2]; // adr de l'entité à laquelle on veut se connecter 
                int port_conn = Integer.parseInt(args[3]); // port de l'entité à laquelle on veut se connecter

                // ouvre une socket
                Socket sock = new Socket(adr_conn, port_conn);

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                sock.getInputStream()));
                PrintWriter pw = new PrintWriter(
                        new OutputStreamWriter(
                                sock.getOutputStream()));

                // recoit le message WELC de la part de l'entité à laquelle on est connecté
                String message = br.readLine();
                System.out.println("Recu : " + message);
                String[] mess_welc = message.split(" ");
                String adr_suiv = mess_welc[1];
                int port_suiv = Integer.parseInt(mess_welc[2]);
                String adr_diff = mess_welc[3];
                int port_diff = Integer.parseInt(mess_welc[4]); 

                // message commence avec "WELC" comme attendu
                if (mess_welc[0].equals("WELC")) { 
                    // envoie réponse
                    String mess_newc = "NEWC " + mon_adr + " " + mon_port_udp + "\n";
                    pw.write(mess_newc);
                    pw.flush();
                }
                // message est du mauvais format
                else {
                    System.out.println("Message mal formé");
                }

                String ack = br.readLine();
                System.out.println("Recu : " + ack);
                if (ack.equals("ACKC")) {
                    String id = AnneauMain.createEntityID();
    
                    // crée nouvelle entité
                    Entity ent = new Entity(id, // id
                            mon_port_udp, // port d'écoute UDP
                            port_tcp, // port TCP
                            adr_suiv, // adresse de machine suivante
                            port_suiv, // port d'écoute de la machine suivante
                            adr_diff, // adresse de multi-diffusion
                            port_diff // port de multi-diffusion
                    );            
                    Thread t = new Thread(ent);
                    t.start();
                }

                br.close();
                pw.close();
                sock.close();
            }
		} 
        catch (Exception e) {
            System.out.println("Error");
			e.printStackTrace();
		}
	}
}
