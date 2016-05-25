/* Recevoir les messages du serveur et les imprimer
 * à stdout
*/

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import java.util.Objects;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.DatagramChannel;
import java.nio.ByteBuffer;
import java.lang.Math;
import java.lang.*;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;

public class Entity implements Runnable {
    public String id;
    public int port_ecoute; // port d'écoute
    private int port_tcp;
    public String adr_suiv;
    public int port_suiv;
    public String adr_diff; // adresse de multi-diffusion
    public int port_diff; // port de multi-diffusion 

    private ArrayList<String> deja_vus = new ArrayList<String>(); // messages que cette entité a déjà vu

    public static boolean broken;

    public Entity(String id, int pe, int pt, String as, int ps, String ad, int pd) {
        this.id = id;
        this.port_ecoute = pe;
        this.port_tcp = pt;
        this.adr_suiv = as;
        this.port_suiv = ps;
        this.adr_diff = ad;
        this.port_diff = pd;
    }

    /* Méthode pour accepter une connexion TCP (pour insérer une nouvelle
       entité dans l'anneau) OU
       pour recevoir un message de la machine précédente de l'anneau,
       puis le transmettre à la prochaine machine de l'anneau à la condition
       qu'il n'a pas encore fait le tour de l'anneau
    */
    public void run() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            // commence un thread pour attendre l'entrée au stdin
            StartMessages startmess = new StartMessages(this);
            Thread t_startmess = new Thread(startmess);
            t_startmess.start();

            Selector sel = Selector.open();

            // ouvre une socket juste pour envoyer - pas nécessaire de spécifier le port
            DatagramSocket dso = new DatagramSocket();

            // ouvre des canaux
            ServerSocketChannel srv = ServerSocketChannel.open();
            DatagramChannel dc_ec = DatagramChannel.open(); // canal pour port d'écoute UDP
            srv.configureBlocking(false);
            dc_ec.configureBlocking(false);

            // canal TCP pour attendre les demandes d'insertion
            srv.socket().bind(new InetSocketAddress(this.port_tcp));
            // canal UDP pour transmettre les messages dans l'anneau
            dc_ec.bind(new InetSocketAddress(this.port_ecoute));

            srv.register(sel, SelectionKey.OP_ACCEPT);
            dc_ec.register(sel, SelectionKey.OP_READ);

            // configure pour multicast
            DatagramChannel dc_diff = DatagramChannel.open(); // canal pour multicast
            dc_diff.configureBlocking(false);

            dc_diff.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            NetworkInterface ni = NetworkInterface.getByName("en1");
            dc_diff.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
            // faire marcher le multicast dans la même machine
            //dc_diff.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);

            // canal pour écouter des multicasts
            dc_diff.bind(new InetSocketAddress(this.port_diff));
            dc_diff.register(sel, SelectionKey.OP_READ);

            // joindre le groupe de multicast
            InetAddress multicast_group = InetAddress.getByName(this.adr_diff);
            dc_diff.join(multicast_group, ni);

            while (true) {
                ByteBuffer buff = ByteBuffer.allocate(1024);

                sel.select();
                Iterator<SelectionKey> it = sel.selectedKeys().iterator();

                while(it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    // TCP CONNEXION (pour insérer nouvelle entité)
                    if (key.isAcceptable()) {
                        // accepte connexion de la part de la nouvelle entité
                        SocketChannel client = srv.accept();
                        client.configureBlocking(false);
                        // envoie le message WELC
                        String mess_welc = "WELC " + this.adr_suiv + " " +
                                this.port_suiv + " " + this.adr_diff + " " +
                                this.port_diff + "\n";
                        byte[] tcp_data = mess_welc.getBytes();
                        ByteBuffer source = ByteBuffer.wrap(tcp_data);
                        client.write(source);

                        // recoit le message NEWC
                        int bytes_read;
                        // attend l'envoi du message
                        while((bytes_read = client.read(buff)) == 0) {
                            ;
                        }
                        String mess_newc = new String(buff.array(), 0, buff.array().length);
                        String[] mess_newc_mots = mess_newc.trim().split(" "); 
                        System.out.println("Recu : " + mess_newc);
                        buff.clear();

                        String adr_new = mess_newc_mots[1]; // adresse de nouvelle entité
                        int port_ec_new = Integer.parseInt(mess_newc_mots[2]); // port d'écoute de nouvelle entité

                        // message commence avec "NEWC" comme attendu
                        if (mess_newc_mots[0].equals("NEWC")) {
                            client.write(ByteBuffer.wrap(("ACKC\n").getBytes()));
                            client.close();

                            // met nouvelle entité après cette entité
                            this.adr_suiv = adr_new;
                            this.port_suiv = port_ec_new;
                        }
                        // message est du mauvais format
                        else {
                            System.out.println("Message mal formé");
                        }
                    }

                    // UDP MESSAGE (pour faire le tour de l'anneau)
                    else if(key.isReadable() && 
                            key.channel() == dc_ec) {
                        // recoit le message
                        dc_ec.receive(buff);
                        String message = new String(buff.array(), 0, buff.array().length);
                        buff.clear();
                        
                        System.out.println("MESSAGE RECIEVED: "+ message);
                        // message est trop long
                        if (message.trim().length() > 512) {
                            System.out.println("Le message est trop long. Il ne sera " +
                                    "pas retransmis.");
                        }
                        // message est bon
                        else {
                            // message divisé par les espaces
                            String[] mess_mots = message.split(" ");

                            InetSocketAddress ia = new 
                                    InetSocketAddress(this.adr_suiv, this.port_suiv);
                            System.out.println("Recu THIS IS PRINTING : " + message);
                            //String mess_id = (mess_mots[1]).trim(); // message id
                            String mess_id = mess_mots[1]; // message id
                                                  
                            //String mess_id = Objects.toString(createID());

                            broken = true;

                            // EYBG message revient à l'entité souhaitant sortir de l'anneau
                            if (mess_mots[0].equals("EYBG")) {
                                this.adr_suiv = null;
                                this.port_suiv = -1;
                                System.out.println("Entité exclue de l'anneau.");
                            }
                            // TEST message revient à l'entité qui l'a envoyé
                            else if (mess_mots[0].equals("TEST") && (this.deja_vus).contains(mess_id)) {
                                broken = false;
                            }
                            
                            
                            // voir si le message a déjà fait le tour de l'anneau
                            else if (!(this.deja_vus).contains(mess_id)) {
                                (this.deja_vus).add(mess_id);

                                boolean gbye_pred = false; // si l'entité actuelle précède l'entité souhaitant sortir 
                                if((mess_mots[0]).equals("GBYE")) {
                                    gbye_pred = gbye(this, mess_mots);
                                }
                                                    
                                // entité actuelle précède l'entité souhaitant sortir de l'anneau
                                if(gbye_pred) {
                                    String eybg_mess = "EYBG " + mess_id;
                                    byte[] udp_data = eybg_mess.getBytes();
                                    DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                            udp_data.length, ia);

                                    System.out.println("En train d'envoyer... " + eybg_mess); 
                                    dso.send(paquet_send);

                                    String gbye_adr_suiv = mess_mots[4];
                                    int gbye_port_suiv = Integer.parseInt((mess_mots[5]).trim());
                                    this.adr_suiv = gbye_adr_suiv;
                                    this.port_suiv = gbye_port_suiv;
                                }
                              
                                //NEW CODE
                                //IF RECEIVES MESSAGE APPL TRANS### REQ THEN LOOK FOR FILE
                                if((mess_mots[0]).equals("APPL") && (mess_mots[2]).equals("TRANS###") && (mess_mots[3]).equals("REQ")){
                                	String fName = mess_mots[5].trim();
                                	String messId = mess_mots[1].trim();
                                	String currentDir = System.getProperty("user.dir");
                                	String path = currentDir.trim() + "/" + fName.trim();
                                	System.out.println("CURRENT FILE PATH : " + path);
                                	//if finds file 
                                	if (findFile(fName, currentDir)){
                                		//send message right before transmission
                                		File file = new File(path);
                                		int len = (int) file.length();
                                		
                                		//finding the possible size
                                		int no_mess = 0;
                                		String mess_poss;
                                		String trans_id = Objects.toString(createID());
                                		
                                		mess_poss = "APPL " + messId + " TRANS### SEN " + trans_id + " " + no_mess + "  ";
                                		byte[] mess_size = mess_poss.getBytes();
                                		int poss_size = 512 - mess_size.length;
                                		//creeant la variable nummess
                                		double d = len/poss_size;
                                		if (d<1){
                                			d = 1.0;
                                		}else {
                                			d = Math.ceil(d);
                                		}
                                		int nummess = (int)d;
                                	                                		
                                		//envoye le message decrivant combien de messages il va envoyer
                                		message = "APPL " + messId + " TRANS### ROK " + trans_id + " " +
                                                fName.length() + " " + fName + " " + nummess;	
                                		byte[] udp_data = message.getBytes();
                                        DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                                udp_data.length, ia);
                                        System.out.println("En train d'envoyer... " + message); 
                                        dso.send(paquet_send); 
                                        //System.out.println("ROK PACKET LENGTH : "+ udp_data.length);
                                        
                                        //continue à envoyer les autres messages
                                		byte[] mess = new byte[len];
                                		FileInputStream in = new FileInputStream(file);
                                		int n = 0;
                                		int bytes_read = 0;
                                		int left = len;
                                		while((bytes_read < len) && (n != -1)){        
                                			
                                			String messIdSen = Objects.toString(createID());
                                			if (left >= poss_size){
                                				n = in.read(mess, bytes_read, poss_size);
                                				//System.out.println("mess = " + mess);
                                				bytes_read += n;
                                				left = left - poss_size;
                                				String m = new String(mess);
                                				
                                				//sending message
                                				message = "APPL " + messIdSen + " TRANS### SEN " + trans_id + " " + no_mess + " " +
                                						m.length() + " " + m;	
                                				udp_data = message.getBytes();
                                				paquet_send = new DatagramPacket(udp_data, 
                                						udp_data.length, ia);
                                				System.out.println("En train d'envoyer... " + message); 
                                				//System.out.println("greater");
                                				dso.send(paquet_send);
                                			} else if (left < poss_size) {
                                				n = in.read(mess, bytes_read, left);
                                				bytes_read += n;
                                				left = 0;
                                				//System.out.println("MESSAGE" );
                                				String m = new String(mess);
                                				//System.out.println(m);
                                				//sending message
                                				message = "APPL " + messIdSen + " TRANS### SEN " + trans_id + " " + no_mess + " " +
                                						m.length() + " " + m;	
                                				udp_data = message.getBytes();
                                				paquet_send = new DatagramPacket(udp_data, 
                                						udp_data.length, ia);
                                				System.out.println("En train d'envoyer... " + message); 
                                				//System.out.println("smaller");
                                				dso.send(paquet_send);
                                			}
                                			no_mess += 1;
                                		}                                	 
                                		//start reading file, 
                                		//then need to send it to whoever requested it and write it
                                		//also needs to stop the current message 
                                	} /*else {
                                		//proceed to next machine to search it
                                		// transmet le message à la prochaine machine
                                        byte[] udp_data = message.getBytes();
                                        DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                                udp_data.length, ia);

                                        System.out.println("En train d'envoyer... " + message); 
                                        dso.send(paquet_send);
                                	}*/
                                	
                                }
                                //NEW CODE
                                //IF FILE == ROK
                                if((mess_mots[0]).equals("APPL") && (mess_mots[2]).equals("TRANS###") && (mess_mots[3]).equals("ROK")){
                                	System.out.println("RECIEVED A MESSAGE WITH ROK FIRST");
                                	String fName = mess_mots[6].trim();
                                	int nummess = Integer.parseInt(mess_mots[7].trim());
                                	String trans_id = mess_mots[4].trim();
                                	String currentDir = System.getProperty("user.dir");
                                	String path = currentDir.trim() + "/" + fName.trim();
                                	System.out.println("CURRENT FILE PATH ROK : " + path);
                                	File file = new File(path);
                                	// if file doesnt exists, then create it
                        			if (!file.exists()) {
                        				file.createNewFile();
                        			}
                        			FileOutputStream outputStream = new FileOutputStream(fName);
                                	for (int i=0; i<nummess; i++){
                                		// recoit le message
                                        dc_ec.receive(buff);
                                        System.out.println("HERE IS THE BUFFER " );
                                        String messageSEN = new String(buff.array(), 0, buff.array().length);
                                        buff.clear();
                                        
                                        //InetSocketAddress ia = new InetSocketAddress(this.adr_suiv, this.port_suiv);
                                        System.out.println("Recu SEN: " + messageSEN);
                                        
                                        String[] mess_mots1 = messageSEN.split(" ");
                                        String encCont = mess_mots1[7];
                                        byte[] encBuf = encCont.getBytes();               
                                        outputStream.write(encBuf); 
                                	}
                                	outputStream.close();
                                }
                                
                                
                    			
                                
                                else {
                                    // transmet le message à la prochaine machine
                                    byte[] udp_data = message.getBytes();
                                    DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                                            udp_data.length, ia);

                                    System.out.println("En train d'envoyer... " + message); 
                                    dso.send(paquet_send);
                                    
                                    // WHOS message
                                    if ((mess_mots[0]).equals("WHOS")) {
                                        String memb_id = whos(this.id, this.port_ecoute, dso, ia);
                                        this.deja_vus.add(memb_id);
                                    }
                                }
                            } 
                          //NEW CODE
                            //IF FILE == ROK
                            //testline added && statement
                            if((mess_mots[0]).equals("APPL") && (mess_mots[2]).equals("TRANS###") && (mess_mots[3]).equals("ROK") && (this.id).equals(StartMessages.startId) ){
                            	System.out.println("RECIEVED A MESSAGE WITH ROK SECOND");
                            	String fName = mess_mots[6].trim();
                            	int nummess = Integer.parseInt(mess_mots[7].trim());
                            	String trans_id = mess_mots[4].trim();
                            	String currentDir = System.getProperty("user.dir");
                            	String path = currentDir.trim() + "/" + fName.trim();
                            	System.out.println("CURRENT FILE PATH ROK : " + path);
                            	File file = new File(path);
                            	// if file doesnt exists, then create it
                    			if (!file.exists()) {
                    				file.createNewFile();
                    			}
                    			FileOutputStream outputStream = new FileOutputStream(fName);
                            	for (int i=0; i<nummess; i++){
                            		// recoit le message
                                    dc_ec.receive(buff);
                                    String messageSEN = new String(buff.array(), 0, buff.array().length);
                                    buff.clear();
                                    
                                    //InetSocketAddress ia = new InetSocketAddress(this.adr_suiv, this.port_suiv);
                                    System.out.println("Recu SEN: " + messageSEN);
                                    
                                    String[] mess_mots1 = messageSEN.split(" ");
                                    String encCont = mess_mots1[7];
                                    byte[] encBuf = encCont.getBytes();               
                                    outputStream.write(encBuf); 
                            	}
                            	outputStream.close();
                            }
                        }
                    }
                    // MULTICAST
                    else if(key.isReadable() && 
                            key.channel() == dc_diff) {
                        dc_diff.receive(buff);
                        String st = new String(buff.array(), 0, buff.array().length);
                        st = st.trim();
                        System.out.println("Recu : " + st);
                        buff.clear();

                        // DOWN - anneau doit se terminer
                        if (st.equals("DOWN")) {
                            System.out.println("En train de fermer la connexion...");
                            dso.close();
                            System.exit(0);
                        }
                    }
                    else {
                        System.out.println("Error");
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Pour traiter d'un message de la forme :
     * WHOS idm
    */
    private String whos(String ent_id, int port, 
            DatagramSocket dso, InetSocketAddress ia) {

        String mess_id_str = "";
        try {
            long mess_id = createID();
            String adr = InetAddress.getLocalHost().getHostAddress();
            String memb_mess = "MEMB " + mess_id + " " + ent_id +
                    " " + adr + " " + port;

            byte[] udp_data = memb_mess.getBytes();
            DatagramPacket paquet_send = new DatagramPacket(udp_data, 
                    udp_data.length, ia);

            System.out.println("En train d'envoyer... " + memb_mess); 
            dso.send(paquet_send);

            mess_id_str += mess_id;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return mess_id_str;
    }

    /* Pour voir si un message GBYE est arrivé à son
     * prédécesseur dans l'anneau
     * GBYE idm ip port ip-succ port-succ
    */
    private boolean gbye(Entity ent, String[] gbye_mess) {
    
        boolean pred = false;
        try {
            String gbye_adr = gbye_mess[2]; // adresse de l'entité souhaitant sortir
            int gbye_port = Integer.parseInt(gbye_mess[3]); // port de l'entité souhaitant sortir

            if (((this.adr_suiv).equals(gbye_adr) && ((this.port_suiv) == gbye_port))) {
                pred = true;   
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return pred; 
    }

    //NEW CODE
    /* Pour chercher pour le ficher dans le dossier donnée
    */
    private boolean findFile(String file, String path){
    	//System.out.println("trying to find file: ");
    	//System.out.print(file);
    	//System.out.println("trying to find path: ");
    	//System.out.print(path);
    	File folder = new File(path);
    	boolean found = false;
    	if (folder.isDirectory()){
    		File[] listOfFiles = folder.listFiles();
    		if(listOfFiles.length < 1){
    			//pas de fichers dans le dossier
    		} else {
    			String nameF = "";
    			for (File f : listOfFiles){
    				nameF = f.getName().trim();
    				if (nameF.equals(file.trim())){
    					found = true;
    					System.out.println("FOUND IT!!!");
    				}
    			}
    		}
    		return found;
    	} 
    	return found;
    }
    
    /* Pour lire et transmettre le message
    */

    /* Pour ecrire et transferer le message
    */
    
    // crée des IDs uniques de 8 octets
    private static long createID() {
        UUID u = UUID.randomUUID();
        long ul = u.getLeastSignificantBits(); // 8 octets
        long ul_abs = Math.abs(ul); // valeur absolue

        return ul_abs;
    }
}
