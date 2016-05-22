/* Basic starting point for ring network 
 * 1 entity with randomly generated ID
 *     - UDP port 10001
 *     - TCP port 19001
 *     - Multicast address: 225.1.2.4
 *     - Multicast port: 8888
 *
 * MUST RUN WITH -Djava.net.preferIPv4Stack=true FLAG:
 * java -Djava.net.preferIPv4Stack=true AnneauMain 
 *
 * To insert new entity, run:
 * java -Djava.net.preferIPv4Stack=true InsertEntity <mon_port_udp> <mon_port_tcp> <adresse_pour_se_connecter> <port_tcp_pour_se_connecter>
 *
 * TEST BY INSERTING MORE ENTITIES, 
 * THEN ENTER MESSAGES BY USING NETCAT OR 
 * BY TYPING THEM ON STDIN
 *
 * FOR NOW, ALL MACHINES ARE ON LOCALHOST (127.0.0.1)
*/

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.UUID;
import java.lang.Math;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.Iterator;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.DatagramChannel;
import java.nio.ByteBuffer;

public class AnneauMain {

	public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack" , "true");
		try {
            long id = createID();
            String local_adr = InetAddress.getLocalHost().getHostAddress();

            // multicast
            Selector sel = Selector.open();
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(ip);

            DatagramChannel dc_diff = DatagramChannel.open(); // canal pour multicast
            dc_diff.configureBlocking(false);
            // canal pour écouter des multicasts
            dc_diff.bind(new InetSocketAddress(8888));
            dc_diff.register(sel, SelectionKey.OP_READ);
            dc_diff.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            dc_diff.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
    
            InetAddress multicast_group = InetAddress.getByName("225.1.2.4");
            dc_diff.join(multicast_group, ni);

            Entity ent = new Entity(id, // id
                    10001, // port d'écoute
                    19001, // port TCP
                    local_adr, // adresse de machine suivante
                    10001, // port d'écoute de la machine suivante
                    "225.1.2.4", // adresse de multi-diffusion
                    8888 // port de multi-diffusion
            );            

            // créer & commencer les threads
            Thread t = new Thread(ent);
            t.start();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static long createID() {
        UUID u = UUID.randomUUID();
        long ul = u.getLeastSignificantBits(); // 8 octets
        long ul_abs = Math.abs(ul); // valeur absolue

        return ul_abs;
    }
}
